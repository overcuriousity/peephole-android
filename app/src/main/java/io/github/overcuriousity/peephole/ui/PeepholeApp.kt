package io.github.overcuriousity.peephole.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.overcuriousity.peephole.PeepholeApplication
import io.github.overcuriousity.peephole.net.PeepholeApi
import io.github.overcuriousity.peephole.store.Instance

@Composable
fun PeepholeApp(app: PeepholeApplication) {
    val nav = rememberNavController()
    val store = app.store
    // Activity scoped: survives the scan → home/pair navigation, never saved.
    val handoff = viewModel<ScanHandoff>()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            InstanceHome(
                store = store,
                theme = app.theme,
                apiFor = app::api,
                jobs = app.jobs,
                handoff = handoff,
                onScan = { nav.navigate("scan") },
                onPair = { nav.navigate("pair") },
                onManage = { nav.navigate("instances") },
                onSettings = { id -> nav.navigate("instance/$id/settings") },
                onOpenIp = { instance, addr -> nav.navigate("instance/${instance.id}/ip/${Uri.encode(addr)}") },
                onOpenJob = { instance, job -> nav.navigate("instance/${instance.id}/job/${Uri.encode(job)}") },
                onRevoked = { instance -> nav.revoked(instance.id) },
            )
        }
        composable("scan") {
            ScanScreen(
                handoff = handoff,
                onPairing = { nav.navigate("pair") { popUpTo("scan") { inclusive = true } } },
                onLookup = { nav.popBackStack("home", inclusive = false) },
                onClose = { nav.popBackStack() },
            )
        }
        composable("instances") {
            InstanceListScreen(
                store = store,
                onPair = { nav.navigate("pair") },
                onOpen = { id ->
                    store.lastUsedId = id
                    nav.popBackStack("home", inclusive = false)
                },
            )
        }
        composable("pair") {
            PairScreen(
                store = store,
                client = app.pairingClient,
                handoff = handoff,
                onClose = { nav.popBackStack() },
            )
        }
        composable("instance/{id}/ip/{addr}") { entry ->
            val addr = entry.arguments?.getString("addr").orEmpty()
            Paired(app, entry, nav) { instance, api ->
                IpDetailScreen(
                    api = api,
                    instance = instance,
                    addr = addr,
                    onBack = { nav.popBackStack() },
                    onProbe = { nav.navigate("instance/${instance.id}/ip/${Uri.encode(addr)}/probe") },
                    onScan = { level -> nav.navigate("instance/${instance.id}/ip/${Uri.encode(addr)}/scan/$level") },
                    onOpenAsn = { nav.navigate("instance/${instance.id}/asn/$it") },
                    onOpenCampaign = { nav.toCampaign(instance.id, it) },
                    onOpenRequest = { nav.navigate("instance/${instance.id}/request/$it") },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/asn/{n}") { entry ->
            val asn = entry.arguments?.getString("n")?.toLongOrNull() ?: 0
            Paired(app, entry, nav) { instance, api ->
                AsnScreen(
                    api = api,
                    instance = instance,
                    asn = asn,
                    onBack = { nav.popBackStack() },
                    onOpenIp = { nav.toIp(instance.id, it) },
                    onOpenCampaign = { nav.toCampaign(instance.id, it) },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/campaign/{key}") { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            Paired(app, entry, nav) { instance, api ->
                CampaignScreen(
                    api = api,
                    instance = instance,
                    key = key,
                    onBack = { nav.popBackStack() },
                    onOpenIp = { nav.toIp(instance.id, it) },
                    onOpenRequest = { nav.navigate("instance/${instance.id}/request/$it") },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/request/{rid}") { entry ->
            val rid = entry.arguments?.getString("rid")?.toLongOrNull() ?: 0
            Paired(app, entry, nav) { instance, api ->
                RequestScreen(
                    api = api,
                    instance = instance,
                    id = rid,
                    onBack = { nav.popBackStack() },
                    onOpenIp = { nav.toIp(instance.id, it) },
                    onOpenRequest = { nav.navigate("instance/${instance.id}/request/$it") },
                    onOpenCampaign = { nav.toCampaign(instance.id, it) },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/ip/{addr}/probe") { entry ->
            val addr = entry.arguments?.getString("addr").orEmpty()
            Paired(app, entry, nav) { instance, api ->
                ProbeScreen(
                    api = api,
                    instance = instance,
                    addr = addr,
                    jobs = app.jobs,
                    onBack = { nav.popBackStack() },
                    onStarted = { job -> nav.toJob(instance.id, job) },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/ip/{addr}/scan/{level}") { entry ->
            val addr = entry.arguments?.getString("addr").orEmpty()
            val level = entry.arguments?.getString("level")?.toIntOrNull()?.coerceIn(1, 5) ?: 1
            Paired(app, entry, nav) { instance, api ->
                ScanConfirmScreen(
                    api = api,
                    instance = instance,
                    addr = addr,
                    level = level,
                    jobs = app.jobs,
                    onBack = { nav.popBackStack() },
                    onBought = { job -> nav.toJob(instance.id, job) },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/job/{job}") { entry ->
            val jobId = entry.arguments?.getString("job").orEmpty()
            val jobs by app.jobs.jobs.collectAsState()
            Paired(app, entry, nav) { instance, api ->
                JobScreen(
                    api = api,
                    instanceName = instance.instanceName,
                    jobId = jobId,
                    ref = jobs.firstOrNull { it.jobId == jobId && it.instanceId == instance.id },
                    onBack = { nav.popBackStack() },
                    onOpenIp = { addr -> nav.navigate("instance/${instance.id}/ip/${Uri.encode(addr)}") },
                    onRevoked = { nav.revoked(instance.id) },
                )
            }
        }
        composable("instance/{id}/revoked") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val name = remember(id) { store.get(id)?.instanceName ?: "this instance" }
            RevokedScreen(
                instanceName = name,
                onPairAgain = { nav.navigate("pair") { popUpTo("home") } },
                onRemove = {
                    store.remove(id)
                    app.jobs.removeInstance(id)
                    nav.popBackStack("home", inclusive = false)
                },
                onClose = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("instance/{id}/settings") { entry ->
            SettingsScreen(
                store = store,
                id = entry.arguments?.getString("id").orEmpty(),
                onPairAnother = { nav.navigate("pair") },
                onUnpaired = { id -> app.jobs.removeInstance(id) },
                onClose = {
                    // After an unpair the screens below this one have nothing to show.
                    val id = entry.arguments?.getString("id").orEmpty()
                    if (store.get(id) == null) nav.popBackStack("home", inclusive = false) else nav.popBackStack()
                },
            )
        }
    }
}

/** Resolves the route's instance and its API, or says it is gone. */
@Composable
private fun Paired(
    app: PeepholeApplication,
    entry: NavBackStackEntry,
    nav: NavHostController,
    content: @Composable (Instance, PeepholeApi) -> Unit,
) {
    val id = entry.arguments?.getString("id").orEmpty()
    val instance = remember(id) { app.store.get(id) }
    val api = remember(id) { app.api(id) }
    if (instance == null || api == null) {
        Text("This instance is no longer paired.", Modifier.padding(16.dp))
        LaunchedEffect(id) { nav.popBackStack("home", inclusive = false) }
        return
    }
    content(instance, api)
}

private fun NavHostController.toIp(id: String, addr: String) = navigate("instance/$id/ip/${Uri.encode(addr)}")

/** A campaign key is an address; an IPv6 one has colons, so it is encoded like one. */
private fun NavHostController.toCampaign(id: String, key: String) = navigate("instance/$id/campaign/${Uri.encode(key)}")

private fun NavHostController.revoked(id: String) = navigate("instance/$id/revoked") { popUpTo("home") }

/** The job replaces the probe/confirm screen, so Back from it returns to the address. */
private fun NavHostController.toJob(id: String, job: String) {
    popBackStack()
    navigate("instance/$id/job/${Uri.encode(job)}")
}
