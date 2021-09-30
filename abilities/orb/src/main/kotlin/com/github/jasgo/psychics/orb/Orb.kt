package com.github.jasgo.psychics.orb

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.TestResult
import com.github.noonmaru.psychics.attribute.EsperAttribute
import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.damage.Damage
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.damage.psychicDamage
import com.github.noonmaru.psychics.task.TickTask
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.trail.trail
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

@Name("orb")
class OrbConcept : AbilityConcept() {

    @Config
    var orbTicks: Int = 200

    init {
        type = AbilityType.ACTIVE
        displayName = "오브"
        range = 32.0
        cost = 80.0
        description = listOf(
            "바다의 심장을 우클릭하여 구체를 생성합니다.",
            "구체는 반경 \${common.range}칸 안의 적을 \${orb.orb-ticks/20}초 동안 자동으로 요격합니다.",
        )
        val item = ItemStack(Material.HEART_OF_THE_SEA)
        val meta: ItemMeta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b$displayName"))
        item.itemMeta = meta
        wand = item
        supplyItems = listOf(item)
        damage = Damage(DamageType.RANGED, EsperStatistic.Companion.of(EsperAttribute.ATTACK_DAMAGE to 0.01))
    }
}
class Orb : Ability<OrbConcept>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this@Orb)
    }
    @EventHandler
    fun onPlayerInteract(event:PlayerInteractEvent) {
        val action = event.action
        if(action == Action.PHYSICAL) return
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (event.item == concept.wand) {
                val result = test()
                if(result != TestResult.SUCCESS) {
                    esper.player.sendActionBar(result.message)
                    return
                }
                val orbTask = OrbTask(esper.player.location.add(0.0, 15.0, 0.0))
                val tickTask = psychic.runTaskTimer(orbTask, 0L, 1L)
                orbTask.task = tickTask
                psychic.consumeMana(concept.cost)
            }
        }
    }
    inner class OrbTask(
        private val loc: Location
    ) : Runnable {
        var task: TickTask? = null
        private var seconds = 0
        override fun run() {
            if (++seconds < concept.orbTicks) {
                createOrb(loc)
                loc.getNearbyLivingEntities(concept.range).forEach { livingEntity ->
                    if(livingEntity.uniqueId != esper.player.uniqueId) {
                        spawnParticles(loc, livingEntity.location)
                        livingEntity.psychicDamage(
                            this@Orb,
                            requireNotNull(concept.damage?.type),
                            esper.getStatistic(requireNotNull(concept.damage?.stats)),
                            esper.player
                        )
                    }
                }
            } else {
                task?.run {
                    cancel()
                    task = null
                }
            }
        }

        private fun createOrb(loc: Location) {
            loc.world.spawnParticle(Particle.END_ROD, loc, 40, 1.0, 1.0, 1.0, 0.01)
        }

        private fun spawnParticles(from: Location, to: Location) {
            trail(from, to, 0.25) { w, x, y, z ->
                w.spawnParticle(
                    Particle.BUBBLE_POP,
                    x, y, z,
                    1,
                    0.05, 0.05, 0.05,
                    0.0,
                    null,
                    true
                )
            }
        }
    }
}