package com.github.jasgo.psychics.beam

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
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.trail.trail
import org.bukkit.*
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

@Name("beam")
class BeamConcept : AbilityConcept() {

    @Config
    var beamTicks = 10

    @Config
    var beamSize = 1.0

    @Config
    var fireTicks = 60

    @Config
    var glowTicks = 60

    init {
        type = AbilityType.ACTIVE
        displayName = "비이이임"
        range = 10.0
        cost = 100.0
        description = listOf(
            "엔드막대기를 우클릭하여 빔을 \${beam.beam-ticks/10}초간 발사합니다.",
            "적에게 적중시 다음 효과를 입힙니다",
            ChatColor.translateAlternateColorCodes('&', "&6발화: \${beam.fire-ticks/20}초"),
            ChatColor.translateAlternateColorCodes('&', "&e발광: \${beam.glow-ticks/20}초"),
        )
        val item = ItemStack(Material.END_ROD)
        val meta: ItemMeta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e$displayName"))
        item.itemMeta = meta
        wand = item
        supplyItems = listOf(item)
        damage = Damage(DamageType.RANGED, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.2))
    }
}
class Beam : Ability<BeamConcept>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this@Beam)
    }
    @EventHandler
    fun onPlayerInteract(event:PlayerInteractEvent) {
        val action = event.action
        if(action == Action.PHYSICAL) return
        if(action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            if(event.item != concept.wand) return
            val result = test()
            if(result != TestResult.SUCCESS) {
                esper.player.sendActionBar(result.message)
                return
            }
            val from = esper.player.eyeLocation
            val direction = from.direction
            from.subtract(direction)
            val beamTask = BeamTask()
            val tickTask = psychic.runTaskTimer(beamTask, 0L, 2L)
            beamTask.tickTask = tickTask
            psychic.consumeMana(concept.cost)
        }
    }
    inner class BeamTask : Runnable {
        var tickTask:TickTask? = null
        private var ticks = 0
        override fun run() {
            if(++ticks < concept.beamTicks) {
                val from = esper.player.eyeLocation
                val direction = from.direction
                from.subtract(direction)
                var to: Location = from.clone().add(direction.clone().multiply(concept.range))
                processRayTrace(from, direction)?.let { to = it }
                spawnParticles(from, to)
            } else {
                tickTask?.run {
                    cancel()
                    tickTask = null
                }
            }
        }
        private fun processRayTrace(from:Location, direction:Vector): Location? {
            from.world.rayTrace(
                from,
                direction,
                concept.range,
                FluidCollisionMode.NEVER,
                true,
                concept.beamSize,
                TargetFilter(esper.player)
            )?.let { rayTraceResult ->
                val to = rayTraceResult.hitPosition.toLocation(from.world)
                rayTraceResult.hitEntity?.let { target ->
                    if (target is LivingEntity) {
                        val damage = requireNotNull(concept.damage)
                        var damageAmount = esper.getStatistic(damage.stats)
                        target.psychicDamage(
                            this@Beam,
                            damage.type,
                            damageAmount,
                            esper.player,
                            from
                        )
                        target.fireTicks = concept.fireTicks
                        target.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, concept.glowTicks, 1, true, false))
                    }
                }
                return to
            }
            return null
        }
        private fun spawnParticles(from: Location, to: Location) {
            trail(from, to, 0.25) { w, x, y, z ->
                w.spawnParticle(
                    Particle.FLAME,
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