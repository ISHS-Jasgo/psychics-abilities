package com.github.jasgo.psychics.transformlink

import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import net.minecraft.server.v1_16_R3.PacketPlayOutPlayerInfo
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.entity.Player
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import org.json.simple.parser.ParseException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection

class SkinGrabber {
    fun changeSkin(player: Player, name: String) {
        try {
            val handle = player.javaClass.getMethod("getHandle").invoke(player)
            val profile = handle.javaClass.getMethod("getProfile").invoke(handle) as GameProfile
            profile.properties.removeAll("textures")
            profile.properties.put("textures", getSkin(name))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @Throws(IOException::class, ParseException::class)
    fun getSkin(name: String): Property {
        val parser = JSONParser()
        val obj1: Any
        var skin = JSONObject()
        val obj: Any = parser.parse(getUUID(name))
        val json: JSONObject = obj as JSONObject
        obj1 = parser.parse(getProfile(json["id"] as String))
        val json1: JSONObject = obj1 as JSONObject
        val raw: JSONObject = json1["raw"] as JSONObject
        val arr: JSONArray = raw["properties"] as JSONArray
        skin = arr[0] as JSONObject
        return Property("textures", skin["value"] as String?, skin["signature"] as String?)
    }

    @Throws(IOException::class)
    fun getUUID(playername: String): String? {
        val url = URL("https://api.minetools.eu/uuid/$playername")
        var connection = url.openConnection() as HttpsURLConnection
        connection.doOutput = true
        connection.requestMethod = "GET"
        val response = StringBuilder()
        val `in` = BufferedReader(
            InputStreamReader(connection.content as InputStream, Charset.forName("UTF-8"))
        )
        connection = url.openConnection() as HttpsURLConnection
        var line: String?
        while (`in`.readLine().also { line = it } != null) {
            response.append(line)
        }
        `in`.close()
        return response.toString()
    }

    @Throws(IOException::class)
    fun getProfile(uuid: String): String? {
        val url = URL("https://api.minetools.eu/profile/$uuid")
        val connection = url.openConnection() as HttpsURLConnection
        connection.doOutput = true
        connection.requestMethod = "GET"
        val response = StringBuilder()
        val `in` = BufferedReader(
            InputStreamReader(connection.content as InputStream, Charset.forName("UTF-8"))
        )
        var line: String?
        while (`in`.readLine().also { line = it } != null) {
            response.append(line)
        }
        `in`.close()
        return response.toString()
    }

    fun reloadPlayer(p: Player) {
        Bukkit.getOnlinePlayers().forEach { pl: Player ->
            (pl as CraftPlayer).handle.playerConnection.sendPacket(
                PacketPlayOutPlayerInfo(
                    PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, pl.handle
                )
            )
        }
        Bukkit.getOnlinePlayers().forEach { pl: Player ->
            (pl as CraftPlayer).handle.playerConnection.sendPacket(
                PacketPlayOutPlayerInfo(
                    PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, pl.handle
                )
            )
        }
        Bukkit.getOnlinePlayers().forEach { pl: Player -> pl.hidePlayer(p) }
        Bukkit.getOnlinePlayers().forEach { pl: Player -> pl.showPlayer(p) }
        val nether = Bukkit.getWorld("world_nether")
        val loc1 = Location(nether, 0.0, 300.0, 0.0)
        val loc2 = Location(
            p.world, p.location.x, p.location.y,
            p.location.z, p.location.yaw, p.location.pitch
        )
        p.teleport(loc1)
        p.teleport(loc2)
    }

    fun setPlayerNameTag(player: Player, name: String?) {
        try {
            val handle = player.javaClass.getMethod("getHandle").invoke(player)
            val obj = handle.javaClass.getMethod("getProfile").invoke(handle)
            val nameField = obj.javaClass.getDeclaredField("name")
            nameField.isAccessible = true
            nameField[obj] = ChatColor.translateAlternateColorCodes('&', name!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun transform(player: Player, name: String) {
        changeSkin(player, name)
        setPlayerNameTag(player, name)
        reloadPlayer(player)
    }
}