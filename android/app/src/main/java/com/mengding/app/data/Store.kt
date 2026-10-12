package com.mengding.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 本地存储。所有读写都在主进程里，文件很小，直接同步写。 */
object Store {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private lateinit var dir: File
    private val _config = MutableStateFlow(ConfigState())
    private val _private = MutableStateFlow(PrivateState())
    private val _notes = MutableStateFlow(NotesState())
    val notes: StateFlow<NotesState> = _notes
    val config: StateFlow<ConfigState> = _config
    val private: StateFlow<PrivateState> = _private

    val cfg get() = _config.value
    val priv get() = _private.value
    val notesNow get() = _notes.value

    /** 便签里的图片、音频放这里（App 私有目录，别的 App 看不到） */
    val mediaDir: File get() = File(dir, "notes_media").also { it.mkdirs() }

    fun init(ctx: Context) {
        dir = ctx.filesDir
        runCatching {
            val f = File(dir, "config.json")
            if (f.exists()) _config.value = json.decodeFromString(ConfigState.serializer(), f.readText())
        }
        runCatching {
            val f = File(dir, "private.bin")
            if (f.exists()) {
                val text = String(Crypto.decrypt(f.readBytes()), Charsets.UTF_8)
                _private.value = json.decodeFromString(PrivateState.serializer(), text)
            }
        }
        runCatching {
            val f = File(dir, "notes.bin")
            if (f.exists()) {
                val text = String(Crypto.decrypt(f.readBytes()), Charsets.UTF_8)
                _notes.value = json.decodeFromString(NotesState.serializer(), text)
            }
        }
    }

    @Synchronized
    fun update(f: (ConfigState) -> ConfigState) {
        val next = f(_config.value)
        if (next == _config.value) return
        _config.value = next
        write("config.json", json.encodeToString(ConfigState.serializer(), next).toByteArray())
    }

    @Synchronized
    fun updatePrivate(f: (PrivateState) -> PrivateState) {
        val next = f(_private.value)
        if (next == _private.value) return
        _private.value = next
        val bytes = json.encodeToString(PrivateState.serializer(), next).toByteArray()
        write("private.bin", Crypto.encrypt(bytes))
    }

    /** 便签文字和个人记录一样加密保存 */
    @Synchronized
    fun updateNotes(f: (NotesState) -> NotesState) {
        val next = f(_notes.value)
        if (next == _notes.value) return
        _notes.value = next
        write("notes.bin", Crypto.encrypt(json.encodeToString(NotesState.serializer(), next).toByteArray()))
    }

    /** 整体替换（导入备份时用） */
    @Synchronized
    fun replaceAll(c: ConfigState, p: PrivateState) {
        _config.value = c
        _private.value = p
        write("config.json", json.encodeToString(ConfigState.serializer(), c).toByteArray())
        write("private.bin", Crypto.encrypt(json.encodeToString(PrivateState.serializer(), p).toByteArray()))
    }

    private fun write(name: String, bytes: ByteArray) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(File(dir, name))
    }
}

/** 用安卓系统密钥库里的 AES 密钥加密，密钥拿不出手机。 */
object Crypto {
    private const val ALIAS = "mengding_private"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }
}
