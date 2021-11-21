package com.github.jasgo.psychics.sniper

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.attribute.EsperAttribute
import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.damage.Damage
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.damage.psychicDamage
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.trail.trail
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import kotlin.math.round
import kotlin.random.Random

@Name("sniper")
class SniperConcept : AbilityConcept() {

    @Config
    var raySize = 0.2

    @Config
    var slowTicks = 30

    @Config
    var glowTicks = 30

    init {
        type = AbilityType.ACTIVE
        displayName = "스나이퍼"
        range = 100.0
        description = listOf(
            "네더라이트 괭이를 우클릭하여 총알을 발사합니다",
            "네더라이트 괭이를 들고 웅크리기 해서 조준을 합니다",
            "적에게 적중시 다음 효과를 입힙니다",
            ChatColor.translateAlternateColorCodes('&', "&9슬로우: \${sniper.slow-ticks/20} 초"),
            ChatColor.translateAlternateColorCodes('&', "&e발광: \${sniper.glow-ticks/20} 초"),
            "적과 거리가 멀 수록 강력한 피해를 입힙니다",
            "적과 거리가 멀 수록 명중률이 떨어집니다",
            "단, 조준을 하고 사격을 할 경우 명중률이 올라갑니다"
        )
        val item = ItemStack(Material.NETHERITE_HOE)
        val meta: ItemMeta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b$displayName"))
        item.itemMeta = meta
        wand = item
        supplyItems = listOf(item)
        damage = Damage(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0))
        cooldownTicks = 50
    }
}

class Sniper : Ability<SniperConcept>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this@Sniper)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action == Action.PHYSICAL) return

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (event.player.inventory.itemInMainHand == concept.wand) {
                if (event.player.getCooldown(event.material) == 0) {
                    val loc1 = event.player.location
                    loc1.pitch -= 10
                    val from = esper.player.eyeLocation
                    val direction = from.direction
                    from.subtract(direction)
                    processRayTrace(from, direction)
                    event.player.teleport(loc1)
                    val loc: Location = from.clone().add(direction.clone().multiply(concept.range))
                    spawnParticles(from, loc)
                    event.player.joomOut()
                    event.player.setCooldown(event.material, concept.cooldownTicks.toInt())
                }
            }
        }
    }

    @EventHandler
    fun onShift(event: PlayerToggleSneakEvent) {
        if (!event.player.isSneaking) {
            if (event.player.inventory.itemInMainHand == concept.wand) {
                event.player.joomIn()
            }
        } else {
            event.player.joomOut()
        }
    }

    private fun Player.joomIn() {
        this.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 100000, 255, true, false))
        this.addPotionEffect(PotionEffect(PotionEffectType.SLOW_DIGGING, 100000, 250, true, false))
    }

    private fun Player.joomOut() {
        this.removePotionEffect(PotionEffectType.SLOW)
        this.removePotionEffect(PotionEffectType.SLOW_DIGGING)
    }

    private fun processRayTrace(from: Location, direction: Vector): Location? {
        from.world.rayTrace(
            from,
            direction,
            concept.range,
            FluidCollisionMode.NEVER,
            true,
            concept.raySize,
            TargetFilter(esper.player)
        )?.let { rayTraceResult ->
            val to = rayTraceResult.hitPosition.toLocation(from.world)
            rayTraceResult.hitEntity?.let { target ->
                if (target is LivingEntity) {
                    val damage = requireNotNull(concept.damage)
                    val damageAmount =
                        esper.getStatistic(damage.stats) * (esper.player.location.distance(target.location) * 0.15)
                    if (esper.player.isSneaking) {
                        target.psychicDamage(
                            this@Sniper,
                            damage.type,
                            damageAmount,
                            esper.player,
                            from,
                            4.0
                        )
                        target.addPotionEffect(
                            PotionEffect(
                                PotionEffectType.GLOWING,
                                concept.glowTicks,
                                1,
                                true,
                                false
                            )
                        )
                        target.addPotionEffect(PotionEffect(PotionEffectType.SLOW, concept.slowTicks, 2, true, false))
                    } else {
                        if (bulletSpread(esper.player.location.distance(target.location))) {
                            target.psychicDamage(
                                this@Sniper,
                                damage.type,
                                damageAmount,
                                esper.player,
                                from,
                                4.0
                            )
                            target.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.GLOWING,
                                    concept.glowTicks,
                                    1,
                                    true,
                                    false
                                )
                            )
                            target.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.SLOW,
                                    concept.slowTicks,
                                    2,
                                    true,
                                    false
                                )
                            )
                        }
                    }
                }
            }
            return to
        }
        return null
    }

    private fun spawnParticles(from: Location, to: Location) {
        trail(from, to, 0.25) { w, x, y, z ->
            w.spawnParticle(
                Particle.CRIT,
                x, y, z,
                1,
                0.05, 0.05, 0.05,
                0.0,
                null,
                true
            )
        }
    }

    private fun bulletSpread(distance: Double): Boolean {
        val dis = round(distance * 10)
        val random = Random
        val rInt = random.nextInt(1000)
        return rInt >= dis
    }
}