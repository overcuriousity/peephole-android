package io.github.overcuriousity.peephole

import android.app.Application
import android.content.Context
import io.github.overcuriousity.peephole.pairing.PairingClient
import io.github.overcuriousity.peephole.store.InstanceStore
import io.github.overcuriousity.peephole.store.KeystoreTokenCipher
import io.github.overcuriousity.peephole.store.PrefsKeyValue

class PeepholeApplication : Application() {
    val store: InstanceStore by lazy {
        InstanceStore(PrefsKeyValue(getSharedPreferences("instances", Context.MODE_PRIVATE)), KeystoreTokenCipher())
    }
    val pairingClient: PairingClient by lazy { PairingClient() }
}
