package com.github.jasgo.psychics.transformlink

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.TestResult
import com.github.noonmaru.psychics.task.TickTask
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.effect.playFirework
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.*
import kotlin.collections.HashMap
import kotlin.random.Random.Default.nextFloat

@Name("transformer")
class TransformConcept : AbilityConcept() {
    @Config
    var raysize: Double

    @Config
    var rayDelayTicks: Long

    init {
        type = AbilityType.ACTIVE
        displayName = "트랜스폼 링크"
        cost = 20.0
        description = listOf("")
        range = 32.0
        val item = ItemStack(Material.STICK)
        val meta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b$displayName"))
        meta.lore = description
        item.itemMeta = meta
        supplyItems = listOf(item)
        wand = item
        raysize = 0.5
        rayDelayTicks = 8L
    }
}

class MyAbility : Ability<TransformConcept>(), Listener {

    val link: HashMap<UUID, UUID> = HashMap()
    val origin: HashMap<Player, String> = HashMap()

    override fun onEnable() {
        psychic.registerEvents(this@MyAbility)
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        val testResult = test()

        if (testResult != TestResult.SUCCESS) {
            esper.player.sendActionBar(testResult.getMessage(this))
            return
        }
        if (event.item == concept.wand) {
            if (!link.containsKey(event.player.uniqueId)) {
                cooldownTicks = concept.cooldownTicks
                psychic.consumeMana(concept.cost)
                val from = event.player.location.add(0.0, 1.0, 0.0)
                val direction = from.direction
                from.subtract(direction)
                val magicTask = MagicTask(from, from.direction, concept.range)
                val tickTask = psychic.runTaskTimer(magicTask, 0L, 1L)
                magicTask.task = tickTask
            } else {
                link[esper.player.uniqueId]?.let {
                    esper.player.sendMessage(
                        ChatColor.translateAlternateColorCodes(
                            '&',
                            "&l&4당신은 이미 " + Bukkit.getPlayer(it)?.name + "님과 링크되어 있습니다!"
                        )
                    )
                }
            }
        }
    }

    @EventHandler
    fun onDeath(event: EntityDamageEvent) {
        if (event.entity is Player) {
            if (event.damage >= (event.entity as Player).health) {
                if (link.containsKey((event.entity as Player).uniqueId)) {
                    event.isCancelled = true
                    val heal = link[(event.entity as Player).uniqueId]?.let { Bukkit.getPlayer(it)?.health }
                    (event.entity as Player).health =
                        if ((event.entity as Player).health + heal!! > 20.0) 20.0 else (event.entity as Player).health + heal
                    val pl = link[(event.entity as Player).uniqueId]?.let { Bukkit.getPlayer(it) }
                    pl?.damage(pl.health + 1.0)
                    Bukkit.getOnlinePlayers().forEach { player: Player? ->
                        origin[(event.entity as Player)]?.let {
                            player?.sendMessage(
                                ChatColor.translateAlternateColorCodes(
                                    '&',
                                    "&l&4링크로 인해 " + it + "님 대신 " + pl?.name + "님이 사망하였습니다."
                                )
                            )
                        }
                    }
                    val grabber = SkinGrabber()
                    origin[(event.entity as Player)]?.let { grabber.transform((event.entity as Player), it) }
                    link.remove((event.entity as Player).uniqueId)
                }
            }
        }
    }

    inner class MagicTask(
        private val from: Location,
        private val direction: Vector,
        private val range: Double
    ) : Runnable {
        var task: TickTask? = null
        private var ticks = 0

        override fun run() {
            val world = from.world

            if (++ticks < concept.rayDelayTicks) {
                val x = from.x
                val y = from.y
                val z = from.z

                world.spawnParticle(
                    Particle.PORTAL,
                    x, y, z,
                    10,
                    0.00, 0.00, 0.00,
                    0.005,
                    null,
                    true
                )
            } else {
                task?.run {
                    cancel()
                    task = null
                }
                processRayTrace()
                playSound()
            }
        }

        private fun playSound() {
            from.world.playSound(
                from,
                Sound.BLOCK_PORTAL_TRAVEL,
                1.0F,
                0.8F + nextFloat() * 0.4F
            )
        }

        private fun processRayTrace(): Location? {
            from.world.rayTrace(
                from,
                direction,
                range,
                FluidCollisionMode.NEVER,
                true,
                concept.raysize,
                TargetFilter(esper.player)
            )?.let { rayTraceResult ->
                val to = rayTraceResult.hitPosition.toLocation(from.world)
                rayTraceResult.hitEntity?.let { target ->
                    if (target is Player) {
                        val grabber = SkinGrabber()
                        target.sendMessage(ChatColor.translateAlternateColorCodes('&', "&l&4누군가 당신으로 변장했습니다"))
                        target.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 80, 255, false, false))
                        link[esper.player.uniqueId] = target.uniqueId
                        origin[esper.player] = esper.player.name
                        grabber.transform(esper.player, target.name)
                        esper.player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&b" + target.name + "님과 링크되었습니다!"))
                        val firework = FireworkEffect.builder().with(FireworkEffect.Type.BURST)
                            .withColor(if (rayTraceResult.hitEntity != null) Color.PURPLE else Color.GRAY).build()
                        from.world.playFirework(to, firework)
                    }
                }
                return to
            }

            return null
        }
    }
}
