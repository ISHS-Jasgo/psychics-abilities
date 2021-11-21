package com.github.jasgo.psychics.assassin

import com.github.noonmaru.psychics.Ability
import com.github.noonmaru.psychics.AbilityConcept
import com.github.noonmaru.psychics.AbilityType
import com.github.noonmaru.psychics.util.TargetFilter
import com.github.noonmaru.tap.config.Config
import com.github.noonmaru.tap.config.Name
import org.bukkit.ChatColor
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
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

@Name("assassin")
class AssassinConcept : AbilityConcept() {
    @Config
    var invisibleTicks: Int = 60

    @Config
    var weakTicks: Int = 60

    @Config
    var speedTicks: Int = 60

    @Config
    var strengthTicks: Int = 60

    init {
        type = AbilityType.ACTIVE
        displayName = "기습"
        range = 10.0
        description = listOf(
            "부싯돌 우클릭하여 \${common.range}칸 앞의 타깃의 후방으로 이동합니다",
            "${ChatColor.AQUA}\${assassin.invisible-ticks/20}초 동안 은신합니다",
            "${ChatColor.GREEN}\${assassin.speed-ticks/20}초 동안 빨라집니다",
            "${ChatColor.RED}\${assassin.strength-ticks/20}초 동안 주는 데미지가 증가합니다",
            "${ChatColor.DARK_RED}\${assassin.weak-ticks/20}초 동안 받는 데미지가 증가합니다"
        )
        val item = ItemStack(Material.FLINT)
        val meta: ItemMeta = item.itemMeta
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&c$displayName"))
        item.itemMeta = meta
        wand = item
        supplyItems = listOf(item)
        cooldownTicks = 400
    }
}
class Assassin : Ability<AssassinConcept>(), Listener {
    override fun onEnable() {
        psychic.registerEvents(this@Assassin)
    }
    @EventHandler
    fun onPlayerInteract(event:PlayerInteractEvent) {
        val action = event.action
        if (action == Action.PHYSICAL) return
        if(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if(event.item == concept.wand) {
                val cooldown = esper.player.getCooldown(requireNotNull(event.item).type)
                if(cooldown > 0) {
                    esper.player.sendActionBar("재사용 대기시간: ${cooldown/20}초")
                    return
                }
                val from = esper.player.eyeLocation
                val direction = from.direction
                from.subtract(direction)
                processRaytrace(from, direction)
                esper.player.run {
                    setCooldown(requireNotNull(concept.wand).type, concept.cooldownTicks.toInt())
                }
            }
        }
    }
    private fun processRaytrace(loc:Location, dir:Vector) {
        loc.world.rayTrace(
            loc,
            dir,
            concept.range,
            FluidCollisionMode.NEVER,
            true,
            1.0,
            TargetFilter(esper.player)
        )?.let { rayTraceResult ->
            rayTraceResult.hitEntity?.let { target ->
                if (target is LivingEntity) {
                    val to = target.location
                    val direction = target.eyeLocation.direction
                    val teleport = to.subtract(direction)
                    esper.player.teleport(teleport)
                    esper.player.addPotionEffect(PotionEffect(
                        PotionEffectType.INVISIBILITY, concept.invisibleTicks, 1, true, false
                    ))
                    esper.player.addPotionEffect(PotionEffect(
                        PotionEffectType.WEAKNESS, concept.weakTicks, -2, true, false
                    ))
                    esper.player.addPotionEffect(PotionEffect(
                        PotionEffectType.SPEED, concept.speedTicks, 4, true, false
                    ))
                    esper.player.addPotionEffect(PotionEffect(
                        PotionEffectType.INCREASE_DAMAGE, concept.strengthTicks, 2, true, false
                    ))
                }
            }
        }
    }

}