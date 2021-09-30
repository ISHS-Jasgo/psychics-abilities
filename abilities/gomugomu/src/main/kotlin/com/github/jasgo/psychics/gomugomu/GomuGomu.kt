package com.github.jasgo.psychics.gomugomu

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.damage.psychicDamage
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.effect.playFirework
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.FluidCollisionMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.util.*

@Name("gomgomu")
class GomuGomuConcept : AbilityConcept() {
    init {
        type = AbilityType.PASSIVE
        displayName = "고무고무"
        range = 15.0
        description = listOf()
    }
}

class GomuGomu : Ability<GomuGomuConcept>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this@GomuGomu)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action == Action.PHYSICAL) return
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            processRaytrace()
            val modifiers = event.item?.itemMeta?.getAttributeModifiers(Attribute.GENERIC_ATTACK_DAMAGE)
            if (modifiers != null) {
                esper.player.sendMessage("" + modifiers.size)
            }
        }
    }

    private fun processRaytrace() {
        val loc = esper.player.eyeLocation
        val direction = loc.direction
        esper.player.world.rayTrace(
            loc,
            direction,
            concept.range,
            FluidCollisionMode.NEVER,
            true,
            1.0,
            TargetFilter(esper.player)
        )
            ?.let { rayTraceResult ->
                val to = rayTraceResult.hitPosition.toLocation(loc.world)
                rayTraceResult.hitEntity?.let { target ->
                    if (target is LivingEntity) {
                        target.psychicDamage(
                            this@GomuGomu,
                            DamageType.MELEE,
                            esper.player.itemOnCursor.getAttackDamage(),
                            esper.player,
                            esper.player.location,
                            esper.player.itemOnCursor.getKnockbackForce()
                        )
                        if (rayTraceResult.hitEntity != null) {
                            val firework = FireworkEffect.builder().with(FireworkEffect.Type.BURST)
                                .withColor(Color.RED).build()
                            loc.world.playFirework(to, firework)
                        }
                    }
                }
            }
    }

    private fun ItemStack.getAttackDamage(): Double {
        var damage = 1.0
        val meta = this.itemMeta
        if(meta != null) {
            meta.getAttributeModifiers(Attribute.GENERIC_ATTACK_DAMAGE)?.forEach { attributeModifier ->
                damage = attributeModifier.amount
            }
        }
        return damage
    }

    private fun ItemStack.getKnockbackForce(): Double {
        var damage = 1.0
        val meta = this.itemMeta
        return if(meta != null) {
            meta.attributeModifiers?.forEach { t, u ->
                if (t == Attribute.GENERIC_ATTACK_KNOCKBACK) {
                    damage = u.amount
                }
            }
            damage
        } else {
            1.0
        }
    }
}