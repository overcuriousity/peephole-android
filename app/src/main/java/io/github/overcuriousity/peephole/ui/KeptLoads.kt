package io.github.overcuriousity.peephole.ui

import androidx.lifecycle.ViewModel
import io.github.overcuriousity.peephole.net.Dossier
import io.github.overcuriousity.peephole.net.IpLoad

// Scoped to their back-stack entry by viewModel(): they live while the
// screen is on the stack and go with it. Held in memory only.

class IpPageModel : ViewModel() {
    val kept = Kept<IpLoad>()
}

class DossierModel : ViewModel() {
    val kept = Kept<Dossier<*>>()
}
