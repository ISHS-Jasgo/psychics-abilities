package com.github.jasgo.psychics.iceman

import com.github.noonmaru.psychics.*
import com.github.noonmaru.psychics.attribute.EsperAttribute
import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.damage.Damage
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.damage.psychicDamage
import com.github.noonmaru.psychics.plugin.PsychicPlugin
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import com.github.noonmaru.tap.fake.FakeEntity
import com.github.noonmaru.tap.fake.FakeEntityServer
import com.github.noonmaru.tap.fake.Movement
import com.github.noonmaru.tap.fake.Trail
import com.github.noonmaru.tap.math.copy
import com.github.noonmaru.tap.math.normalizeAndLength
import org.bukkit.*
import org.bukkit.boss.BossBar
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.math.pow

@Name("iceman")
class IcemanConcept : AbilityConcept() {

    @Config
    var projectileReadyTicks = 2

    @Config
    var summonTicks = 2

    @Config
    var projectileTicks = 200

    @Config
    var projectileSpeed = 2.5

    @Config
    var raySize = 1.0

    @Config
    var slowTicks: Int = 3

    @Config
    var frostDamageTicks: Int = 3

    @Config
    var glowTicks: Int = 3

    init {
        type = AbilityType.ACTIVE
        displayName = "아이스맨"
        range = 32.0
        cost = 20.0
        description = listOf(
            "얼음을 우클릭하여 얼음을 발사합니다.",
            "적에게 적중시 다음 효과를 입힙니다",
            ChatColor.translateAlternateColorCodes('&', "&9슬로우: \${iceman.slow-ticks} 초"),
            ChatColor.translateAlternateColorCodes('&', "&e발광: \${iceman.glow-ticks} 초"),
            ChatColor.translateAlternateColorCodes('&', "&b빙결데미지: \${iceman.frost-damage-ticks} 초")
        )
        val item = ItemStack(Material.BLUE_ICE)
        val meta: ItemMeta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&b$displayName"))
        item.itemMeta = meta
        wand = item
        supplyItems = listOf(item)
        damage = Damage(DamageType.RANGED, EsperStatistic.Companion.of(EsperAttribute.ATTACK_DAMAGE to 2.0))
    }
}

class Iceman : Ability<IcemanConcept>(), Listener {

    override fun onEnable() {
        psychic.registerEvents(this@Iceman)
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action == Action.PHYSICAL) return
        if (event.item != concept.wand) return
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            val testResult = test()
            if (testResult != TestResult.SUCCESS) {
                esper.player.sendActionBar(testResult.getMessage(this))
                return
            }
            val eyeLocation = esper.player.eyeLocation
            val direction = eyeLocation.direction
            val bullet = IceBullet(requireNotNull(concept.damage), esper.player.location)
            val projectile = IceBulletProjectile(bullet)
            projectile.velocity = direction.multiply(concept.projectileSpeed)
            bullet.isLaunched = true
            psychic.launchProjectile(eyeLocation, projectile)
            psychic.consumeMana(concept.cost)
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.player.inventory.itemInMainHand == concept.wand)
            event.isCancelled = true
    }

    inner class IceBullet(internal val damage: Damage, private val loc: Location) {
        private var ticks: Int = 0
        private val entity: FakeEntity
        var isLaunched = false
        init {
            entity = psychic.spawnFakeEntity(loc.clone().add(0.0, yOffset(), 0.0), ArmorStand::class.java).apply {
                updateMetadata<ArmorStand> {
                    isMarker = true
                    isVisible = false
                }
                updateEquipment {
                    helmet = ItemStack(Material.BLUE_ICE)
                }
            }
        }

        private fun yOffset(): Double {
            val summonTicks = concept.summonTicks

            if (ticks > summonTicks) return 0.0

            val remainTicks = ticks - summonTicks
            val pow = (remainTicks.toDouble() / summonTicks.toDouble()).pow(2.0)

            return pow * 32.0
        }

        fun update(updateLoc: Location) {
            ticks++
            var yOffset = 0.0
            val yawOffset = 90.0F
            if (!isLaunched) {
                //발사 전에 높이 조절
                yOffset += yOffset()
            }

            this.loc.copy(updateLoc)
            entity.moveTo(updateLoc.clone().apply {
                yaw += yawOffset
                y += yOffset - 1.62
            })
        }

        fun remove() {
            entity.remove()
        }
    }

    inner class IceBulletProjectile(private val bullet: IceBullet) :
        PsychicProjectile(concept.projectileTicks, concept.range) {
        override fun onMove(movement: Movement) {
            if (ticks < concept.projectileReadyTicks)
                movement.to = movement.from.clone().add(velocity.multiply(0.01))
            bullet.update(movement.to)
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let { velocity ->
                val from = trail.from
                val direction = velocity.clone()
                val length = direction.normalizeAndLength()

                from.world.rayTrace(
                    from, direction, length, FluidCollisionMode.NEVER,
                    true, concept.raySize,
                    TargetFilter(esper.player)
                )?.let { result ->
                    remove()

                    result.hitEntity?.let { target ->
                        if (target is LivingEntity) {
                            val damage = bullet.damage
                            val damageAmount = esper.getStatistic(damage.stats)
                            target.psychicDamage(
                                this@Iceman,
                                damage.type,
                                damageAmount,
                                esper.player,
                                result.hitPosition.toLocation(from.world)
                            )
                            target.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.GLOWING,
                                    concept.glowTicks * 20,
                                    1,
                                    true,
                                    false
                                )
                            )
                            target.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.SLOW,
                                    concept.slowTicks * 20,
                                    2,
                                    true,
                                    false
                                )
                            )
                            target.addPotionEffect(
                                PotionEffect(
                                    PotionEffectType.WITHER,
                                    concept.frostDamageTicks * 20,
                                    1,
                                    true,
                                    false
                                )
                            )
                            esper.player.playEffect(
                                target.location.add(0.0, 0.5, 0.0),
                                Effect.STEP_SOUND,
                                Material.BLUE_ICE
                            )
                        }
                    }
                }
            }
        }

        override fun onRemove() {
            bullet.remove()
        }
    }
}