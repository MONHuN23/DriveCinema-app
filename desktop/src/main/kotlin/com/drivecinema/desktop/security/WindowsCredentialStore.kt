package com.drivecinema.desktop.security

import com.sun.jna.LastErrorException
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference

/**
 * Windows (F.10): stores credentials in Windows Credential Manager as a generic credential
 * ("Windows Credentials" → `DriveCinema/<account>`), protected by the user's Windows login.
 * A small hand-written JNA binding to Advapi32, so no jna-platform dependency is needed.
 */
class WindowsCredentialStore(
    /** Tests use their own value so the real entry is never touched. */
    private val account: String = DEFAULT_ACCOUNT
) : CredentialStore {

    override val description = "Windows Credential Manager"
    override val isSystemKeyring = true

    private val target = WString("$TARGET_PREFIX$account")

    override fun load(): StoredCredentials? {
        val result = PointerByReference()
        try {
            advapi.CredReadW(target, CRED_TYPE_GENERIC, 0, result)
        } catch (e: LastErrorException) {
            if (e.errorCode == ERROR_NOT_FOUND) return null
            throw KeyringException("Could not read the sign-in from Credential Manager (error ${e.errorCode})")
        }
        val pointer = result.value ?: return null
        return try {
            val credential = Credential(pointer)
            val blob = credential.credentialBlob ?: return null
            StoredCredentials.fromJson(String(blob.getByteArray(0, credential.credentialBlobSize), Charsets.UTF_8))
        } finally {
            advapi.CredFree(pointer)
        }
    }

    override fun save(credentials: StoredCredentials) {
        val bytes = credentials.toJson().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BLOB_BYTES) {
            throw KeyringException("The sign-in is too large for Credential Manager (${bytes.size} bytes)")
        }
        val blob = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
        val credential = Credential().apply {
            type = CRED_TYPE_GENERIC
            targetName = target
            comment = WString(LABEL)
            credentialBlobSize = bytes.size
            credentialBlob = blob
            persist = CRED_PERSIST_LOCAL_MACHINE
            userName = WString(account)
        }
        try {
            advapi.CredWriteW(credential, 0)
        } catch (e: LastErrorException) {
            throw KeyringException("Could not save the sign-in in Credential Manager (error ${e.errorCode})")
        } finally {
            blob.clear()
        }
    }

    override fun clear() {
        try {
            advapi.CredDeleteW(target, CRED_TYPE_GENERIC, 0)
        } catch (e: LastErrorException) {
            if (e.errorCode != ERROR_NOT_FOUND) {
                throw KeyringException("Could not remove the sign-in from Credential Manager (error ${e.errorCode})")
            }
        }
    }

    /** The W (UTF-16) functions; failures throw [LastErrorException] with the Win32 error code. */
    @Suppress("FunctionName")
    private interface Advapi32 : Library {
        @Throws(LastErrorException::class)
        fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean

        @Throws(LastErrorException::class)
        fun CredWriteW(credential: Credential, flags: Int): Boolean

        @Throws(LastErrorException::class)
        fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean

        fun CredFree(buffer: Pointer)
    }

    /** `CREDENTIALW` from wincred.h; `FILETIME LastWritten` is two DWORDs. */
    @Structure.FieldOrder(
        "flags", "type", "targetName", "comment", "lastWrittenLow", "lastWrittenHigh",
        "credentialBlobSize", "credentialBlob", "persist", "attributeCount", "attributes", "targetAlias", "userName"
    )
    class Credential() : Structure() {
        constructor(pointer: Pointer) : this() {
            useMemory(pointer)
            read()
        }

        @JvmField var flags: Int = 0
        @JvmField var type: Int = 0
        @JvmField var targetName: WString? = null
        @JvmField var comment: WString? = null
        @JvmField var lastWrittenLow: Int = 0
        @JvmField var lastWrittenHigh: Int = 0
        @JvmField var credentialBlobSize: Int = 0
        @JvmField var credentialBlob: Pointer? = null
        @JvmField var persist: Int = 0
        @JvmField var attributeCount: Int = 0
        @JvmField var attributes: Pointer? = null
        @JvmField var targetAlias: WString? = null
        @JvmField var userName: WString? = null
    }

    companion object {
        const val DEFAULT_ACCOUNT = "default"
        const val TARGET_PREFIX = "DriveCinema/"
        private const val LABEL = "DriveCinema – Google Drive sign-in"
        private const val CRED_TYPE_GENERIC = 1
        private const val CRED_PERSIST_LOCAL_MACHINE = 2
        private const val ERROR_NOT_FOUND = 1168
        /** CRED_MAX_CREDENTIAL_BLOB_SIZE (5 × 512); the refresh token JSON is far below it. */
        const val MAX_BLOB_BYTES = 2560

        private val advapi: Advapi32 by lazy { Native.load("Advapi32", Advapi32::class.java) }

        /** True when Credential Manager answers (a lookup of a probe entry succeeds, even if empty). */
        fun isAvailable(): Boolean = try {
            WindowsCredentialStore("availability-probe").load()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
