package io.github.overcuriousity.peephole

import android.app.Application
import android.content.Context
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.pairing.PairingClient
import io.github.overcuriousity.peephole.store.InstanceStore
import io.github.overcuriousity.peephole.store.JobLog
import io.github.overcuriousity.peephole.store.KeystoreTokenCipher
import io.github.overcuriousity.peephole.store.PrefsKeyValue
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class PeepholeApplication : Application() {
    val store: InstanceStore by lazy {
        InstanceStore(PrefsKeyValue(getSharedPreferences("instances", Context.MODE_PRIVATE)), KeystoreTokenCipher())
    }
    val jobs: JobLog by lazy { JobLog(PrefsKeyValue(getSharedPreferences("jobs", Context.MODE_PRIVATE))) }
    val pairingClient: PairingClient by lazy { PairingClient() }

    /** The API of a paired instance, pinned like its pairing was; null once it is unpaired. */
    fun api(instanceId: String): PeepholeApi? {
        val instance = store.get(instanceId) ?: return null
        val token = store.token(instanceId) ?: return null
        val origin = instance.origin.toHttpUrlOrNull() ?: return null
        return PeepholeApi(origin, token, instance.spkiSha256)
    }
}
