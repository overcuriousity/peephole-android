package io.github.overcuriousity.peephole.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.overcuriousity.peephole.pairing.PairingClient
import io.github.overcuriousity.peephole.store.InstanceStore

@Composable
fun PeepholeApp(store: InstanceStore, pairingClient: PairingClient) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "instances") {
        composable("instances") {
            InstanceListScreen(
                store = store,
                onPair = { nav.navigate("pair") },
                onOpen = { id -> nav.navigate("instance/$id/settings") },
            )
        }
        composable("pair") {
            PairScreen(
                store = store,
                client = pairingClient,
                onClose = { nav.popBackStack() },
            )
        }
        composable("instance/{id}/settings") { entry ->
            SettingsScreen(
                store = store,
                id = entry.arguments?.getString("id").orEmpty(),
                onPairAnother = { nav.navigate("pair") },
                onClose = { nav.popBackStack() },
            )
        }
    }
}
