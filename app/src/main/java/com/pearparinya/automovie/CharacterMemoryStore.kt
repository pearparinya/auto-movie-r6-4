package com.pearparinya.automovie

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Profiles are tied to character and exact reference bytes; changing a MASTER never reuses its old profile. */
internal class CharacterMemoryStore(private val folder: File, private val bundled: JSONObject) {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun path(id: String, source: File) = File(folder,"${digest(id.toByteArray())}_${digest(source.readBytes())}.json")
    fun read(id: String, source: File): JSONObject {
        if (!source.exists()) return JSONObject()
        val stored=path(id,source)
        if (stored.exists()) return runCatching { JSONObject(stored.readText()) }.getOrDefault(JSONObject())
        val hash=digest(source.readBytes())
        val profiles=bundled.optJSONArray("profiles") ?: return JSONObject()
        return (0 until profiles.length()).map { profiles.getJSONObject(it) }.firstOrNull {
            it.optString("character_id")==id && it.optString("sha256")==hash
        }?.let { JSONObject(it.toString()) } ?: JSONObject()
    }
    fun save(id: String, source: File, profile: JSONObject) {
        check(source.exists()) { "ขาด MASTER" }
        folder.mkdirs(); val target=path(id,source); val pending=File(folder,target.name+".pending")
        pending.writeText(profile.toString(2)); check(pending.renameTo(target)) { "บันทึกโปรไฟล์ไม่ได้" }
    }
    fun brief(id: String, source: File): String {
        val p=read(id,source)
        return (listOf("face","hair") + BodyReferencePolicy.fields.map { it.first } + "accessories").map { p.optString(it) }.filter { it.isNotBlank() }.joinToString("; ")
    }
}
