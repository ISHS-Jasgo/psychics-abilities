package com.github.jasgo.psychics.bombman

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.TestResult
import com.github.noonmaru.psychics.attribute.EsperAttribute
import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.damage.Damage
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.damage.psychicDamage
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.event.EntityProvider
import com.github.noonmaru.tap.event.TargetEntity
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import kotlin.random.Random.Default.nextFloat

@Name("bombman")
class BombmanConcept : AbilityConcept() {
    init {
        type = AbilityType.ACTIVE
        displayName = "폭탄맨"
        cost = 10.0
        description = listOf(
            "손에 든 폭탄을 우클릭하여 폭탄을 투척합니다"
        )
        val item = ItemStack(Material.TNT)
        val meta: ItemMeta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b$displayName"))
        item.itemMeta = meta
        wand = item
        supplyItems = listOf(item)
        damage = Damage(DamageType.BLAST, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 2.0))
    }
}

class Bombman : Ability<BombmanConcept>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this@Bombman)
    }

    @EventHandler
    fun onPlayerInteract(event:PlayerInteractEvent) {
        val action = event.action
        if(action == Action.PHYSICAL) return
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if(event.item == concept.wand) {
                event.isCancelled = true
                val result = test()
                if (result != TestResult.SUCCESS) {
                    esper.player.sendActionBar(result.message)
                    return
                }
                val snowball: Snowball = esper.player.launchProjectile(Snowball::class.java)
                snowball.item = concept.wand!!
                snowball.shooter = esper.player
                psychic.consumeMana(concept.cost)
            }
        }
    }
    @EventHandler
    @TargetEntity(Shooter::class)
    fun onProjectileHit(event: ProjectileHitEvent) {
        if (event.entity !is Snowball) return
        val snowball: Snowball = event.entity as Snowball
        if(snowball.item != concept.wand) return
        if (event.hitEntity == null) {
            if (event.hitBlock == null) return
            event.hitBlock?.world?.spawnParticle(Particle.EXPLOSION_LARGE, requireNotNull(event.hitBlock).location.add(0.0, 1.0, 0.0), 1, 0.1, 0.1, 0.1)
            playSound()
        } else {
            if (event.hitEntity is LivingEntity) {
                val target: LivingEntity = requireNotNull(event.hitEntity) as LivingEntity
                target.world.spawnParticle(Particle.EXPLOSION_LARGE, target.location.add(0.0, 1.0, 0.0), 1, 0.1, 0.1, 0.1)
                val damageAmount = esper.getStatistic(requireNotNull(concept.damage).stats)
                target.psychicDamage(
                    this@Bombman,
                    DamageType.BLAST,
                    damageAmount,
                    esper.player
                )
                playSound()
            } else {
                event.hitEntity?.world?.spawnParticle(
                    Particle.EXPLOSION_LARGE,
                    requireNotNull(event.hitEntity).location.add(0.0, 1.0, 0.0),
                    1,
                    0.1,
                    0.1,
                    0.1
                )
                playSound()
            }
        }
    }
    private fun playSound() {
        esper.player.world.playSound(
            esper.player.location,
            Sound.ENTITY_GENERIC_EXPLODE,
            1.0F,
            0.8F + nextFloat() * 0.4F
        )
    }
}

class Shooter : EntityProvider<ProjectileHitEvent> {
    override fun getFrom(event: ProjectileHitEvent): Player {
        return event.entity.shooter as Player
    }
}