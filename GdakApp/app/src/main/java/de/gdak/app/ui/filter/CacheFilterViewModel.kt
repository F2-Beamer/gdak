package de.gdak.app.ui.filter

import androidx.lifecycle.ViewModel
import de.gdak.app.data.cachedb.CacheFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Filter und Suche, die Karte und Liste gemeinsam nutzen. Sie werden nicht dauerhaft gespeichert. */
class CacheFilterViewModel : ViewModel() {

    private val _filter = MutableStateFlow(CacheFilter())
    val filter: StateFlow<CacheFilter> = _filter.asStateFlow()

    fun update(change: (CacheFilter) -> CacheFilter) {
        _filter.update(change)
    }
}
