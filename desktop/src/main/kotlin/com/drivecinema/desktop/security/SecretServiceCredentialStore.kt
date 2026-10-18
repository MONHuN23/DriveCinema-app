package com.drivecinema.desktop.security

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * Linux: stores credentials in the user's keyring (GNOME Keyring / KWallet) through libsecret's
 * Secret Service API, so the refresh token is encrypted by the desktop session.
 */
class SecretServiceCredentialStore(
    /** Keyring item attribute; tests use their own value so the real entry is never touched. */
    private val account: String = DEFAULT_ACCOUNT
) : CredentialStore {

    override val description = "system keyring (Secret Service)"
    override val isSystemKeyring = true

    override fun load(): StoredCredentials? {
        val error = PointerByReference()
        val secret = lib.secret_password_lookup_sync(schema, null, error, ATTRIBUTE, account, null)
        throwIfError(error, "read")
        if (secret == null) return null
        return try {
            StoredCredentials.fromJson(secret.getString(0, "UTF-8"))
        } finally {
            lib.secret_password_free(secret)
        }
    }

    override fun save(credentials: StoredCredentials) {
        val error = PointerByReference()
        val ok = lib.secret_password_store_sync(
            schema, COLLECTION_DEFAULT, LABEL, credentials.toJson(), null, error, ATTRIBUTE, account, null
        )
        throwIfError(error, "save")
        if (!ok) throw KeyringException("The keyring did not save the sign-in")
    }

    override fun clear() {
        val error = PointerByReference()
        lib.secret_password_clear_sync(schema, null, error, ATTRIBUTE, account, null)
        throwIfError(error, "clear")
    }

    private fun throwIfError(error: PointerByReference, action: String) {
        val gError = error.value ?: return
        // struct GError { GQuark domain; gint code; gchar *message; }
        val message = gError.getPointer(8)?.getString(0, "UTF-8") ?: "unknown error"
        glib.g_error_free(gError)
        throw KeyringException("Could not $action the sign-in in the keyring: $message")
    }

    @Suppress("FunctionName")
    private interface LibSecret : Library {
        fun secret_password_store_sync(
            schema: SecretSchema, collection: String?, label: String, password: String,
            cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?
        ): Boolean

        fun secret_password_lookup_sync(
            schema: SecretSchema, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?
        ): Pointer?

        fun secret_password_clear_sync(
            schema: SecretSchema, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?
        ): Boolean

        fun secret_password_free(password: Pointer)
    }

    @Suppress("FunctionName")
    private interface GLib : Library {
        fun g_error_free(error: Pointer)
    }

    /** `SecretSchemaAttribute { const gchar *name; SecretSchemaAttributeType type; }` */
    @Structure.FieldOrder("name", "type")
    class SchemaAttribute : Structure() {
        @JvmField var name: String? = null
        @JvmField var type: Int = 0
    }

    /** `SecretSchema` from libsecret's secret-schema.h (32 attributes + reserved fields). */
    @Structure.FieldOrder(
        "name", "flags", "attributes", "reserved",
        "reserved1", "reserved2", "reserved3", "reserved4", "reserved5", "reserved6", "reserved7"
    )
    class SecretSchema : Structure() {
        @JvmField var name: String? = null
        @JvmField var flags: Int = 0
        @JvmField var attributes: Array<SchemaAttribute> = Array(32) { SchemaAttribute() }
        @JvmField var reserved: Int = 0
        @JvmField var reserved1: Pointer? = null
        @JvmField var reserved2: Pointer? = null
        @JvmField var reserved3: Pointer? = null
        @JvmField var reserved4: Pointer? = null
        @JvmField var reserved5: Pointer? = null
        @JvmField var reserved6: Pointer? = null
        @JvmField var reserved7: Pointer? = null
    }

    companion object {
        const val DEFAULT_ACCOUNT = "default"
        private const val SCHEMA_NAME = "com.drivecinema.desktop.GoogleSignIn"
        private const val ATTRIBUTE = "account"
        private const val LABEL = "DriveCinema – Google Drive sign-in"
        private const val COLLECTION_DEFAULT = "default"
        private const val ATTRIBUTE_TYPE_STRING = 0

        private val lib: LibSecret by lazy { loadFirst(listOf("libsecret-1.so.0", "secret-1")) }
        private val glib: GLib by lazy { loadFirst(listOf("libglib-2.0.so.0", "glib-2.0")) }

        /** Kept for the life of the process: libsecret reads it during every call. */
        private val schema: SecretSchema by lazy {
            SecretSchema().apply {
                name = SCHEMA_NAME
                attributes[0].name = ATTRIBUTE
                attributes[0].type = ATTRIBUTE_TYPE_STRING
                write()
            }
        }

        private inline fun <reified T : Library> loadFirst(names: List<String>): T {
            var last: Throwable? = null
            for (name in names) {
                try {
                    return Native.load(name, T::class.java)
                } catch (e: UnsatisfiedLinkError) {
                    last = e
                }
            }
            throw UnsatisfiedLinkError("Could not load ${names.joinToString()}: ${last?.message}")
        }

        /** True when libsecret loads and a keyring answers (a lookup succeeds, even if empty). */
        fun isAvailable(): Boolean = try {
            SecretServiceCredentialStore("availability-probe").load()
            true
        } catch (_: Throwable) {
            false
        }
    }
}

class KeyringException(message: String) : RuntimeException(message)
