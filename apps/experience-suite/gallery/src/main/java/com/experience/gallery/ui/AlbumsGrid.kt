package com.experience.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.experience.gallery.FAVORITES_ID
import com.experience.gallery.VIDEOS_ID
import com.experience.gallery.model.Album
import com.experience.gallery.model.MediaItem

/** Albums (MediaStore buckets) plus Favourites and Videos collections. */
@Composable
fun AlbumsGrid(items: List<MediaItem>, albums: List<Album>, padding: PaddingValues, onOpen: (Album) -> Unit) {
    val special = buildList {
        val favs = items.filter { it.favorite }
        if (favs.isNotEmpty()) add(Album(FAVORITES_ID, "Favourites", favs.first(), favs.size, favs.first().timestamp))
        val videos = items.filter { it.isVideo }
        if (videos.isNotEmpty()) add(Album(VIDEOS_ID, "Videos", videos.first(), videos.size, videos.first().timestamp))
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(special + albums, key = { it.bucketId }) { a ->
            Column(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button) { onOpen(a) }.padding(4.dp)) {
                MediaThumb(a.cover, 180.dp, Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.large))
                Text(a.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                Text("${a.count} item${if (a.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
