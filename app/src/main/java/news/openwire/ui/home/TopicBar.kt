package news.openwire.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import news.openwire.domain.model.Topic

@Composable
fun TopicBar(
    topics: List<Topic>,
    selected: Topic,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(topics, key = { it.id }) { topic ->
            FilterChip(
                selected = topic.id == selected.id,
                onClick = { onSelect(topic.id) },
                label = { Text(topic.label) },
            )
        }
    }
}
