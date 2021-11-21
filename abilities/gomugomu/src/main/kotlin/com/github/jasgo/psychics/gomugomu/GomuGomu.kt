package com.github.jasgo.psychics.gomugomu

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.attribute.EsperAttribute
import com.github.noonmaru.psychics.attribute.EsperStatistic
import com.github.noonmaru.psychics.damage.Damage
import com.github.noonmaru.psychics.damage.DamageType
import com.github.noonmaru.psychics.damage.psychicDamage
import com.github.noonmaru.psychics.tooltip.TooltipBuilder
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.util.*

@Name("gomugomu")
class GomuGomuConcept : AbilityConcept() {
    @Config
    var wooden = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0))

    @Config
    var stone = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.5))

    @Config
    var iron = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 2.0))

    @Config
    var golden = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 2.5))

    @Config
    var diamond = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.0))

    @Config
    var netherite = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 3.5))

    @Config
    var bonusDamageBySharpness = 0.1

    init {
        type = AbilityType.PASSIVE
        displayName = "고무고무"
        range = 15.0
        description = listOf(
            "공격 리치가 \${common.range}로 증가합니다",
            "${ChatColor.RED}이 패시브 효과는 검에만 적용됩니다",
            "나무: <wooden><wooden-damage>",
            "돌: <stone><stone-damage>",
            "철: <iron><iron-damage>",
            "금: <golden><golden-damage>",
            "다이아몬드: <diamond><diamond-damage>",
            "네더라이트: <netherite><netherite-damage>",
            "날카로움 인챈트당 \${gomugomu.bonus-damage-by-sharpness * 100}%의 추가 피해를 입힙니다."
        )
        cooldownTicks = 60
    }
    override fun onRenderTooltip(tooltip: TooltipBuilder, stats: (EsperStatistic) -> Double) {
        tooltip.addTemplates(
            "wooden" to stats(wooden.stats),
            "wooden-damage" to wooden,
            "stone" to stats(stone.stats),
            "stone-damage" to stone,
            "iron" to stats(iron.stats),
            "iron-damage" to iron,
            "golden" to stats(golden.stats),
            "golden-damage" to golden,
            "diamond" to stats(diamond.stats),
            "diamond-damage" to diamond,
            "netherite" to stats(netherite.stats),
            "netherite-damage" to netherite
        )
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
            if(event.item == null) return
            val item = requireNotNull(event.item)
            if(item.type.name.contains("SWORD")) {
                val cool = esper.player.getCooldown(requireNotNull(event.item).type)
                if(cool > 0) {
                    esper.player.sendActionBar("재사용 대기시간: ${cool/20}초")
                    return
                }
                processRaytrace()
                val cooldown = concept.cooldownTicks.toInt()

                esper.player.run {
                    setCooldown(Material.WOODEN_SWORD, cooldown)
                    setCooldown(Material.STONE_SWORD, cooldown)
                    setCooldown(Material.IRON_SWORD, cooldown)
                    setCooldown(Material.GOLDEN_SWORD, cooldown)
                    setCooldown(Material.DIAMOND_SWORD, cooldown)
                    setCooldown(Material.NETHERITE_SWORD, cooldown)
                }
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
                rayTraceResult.hitEntity?.let { target ->
                    if (target is LivingEntity) {
                        val item = esper.player.inventory.itemInMainHand
                        val damage = getAttackDamage(item)
                        val sharpness = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL)
                        val knockback = item.getEnchantmentLevel(Enchantment.KNOCKBACK)
                        var damageAmount = esper.getStatistic(damage.stats)
                        damageAmount *= 1.0 + sharpness * concept.bonusDamageBySharpness
                        target.psychicDamage(
                            this@GomuGomu,
                            DamageType.MELEE,
                            damageAmount,
                            esper.player,
                            rayTraceResult.hitPosition.toLocation(loc.world),
                            1.0 + knockback.toDouble() * 2.0
                        )
                        if (rayTraceResult.hitEntity != null) {
                            loc.world.spawnParticle(Particle.CRIT, target.location.add(0.0, 1.0, 0.0), 20, 0.1, 0.1, 0.1)
                        }
                    }
                }
            }
    }

    private fun getAttackDamage(item: ItemStack): Damage {
        var default = Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 1.0))
        if (item.type.name.contains("SWORD")) {
            default = when (item.type) {
                Material.WOODEN_SWORD -> concept.wooden
                Material.STONE_SWORD -> concept.stone
                Material.IRON_SWORD -> concept.iron
                Material.GOLDEN_SWORD -> concept.golden
                Material.DIAMOND_SWORD -> concept.diamond
                Material.NETHERITE_SWORD -> concept.netherite
                else -> Damage(DamageType.MELEE, EsperStatistic.of(EsperAttribute.ATTACK_DAMAGE to 0.0))
            }
        }
        return default
    }
}