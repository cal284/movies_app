package com.databounty.movies

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MovieAdapter(private val onClick: (Movie) -> Unit) : RecyclerView.Adapter<MovieAdapter.VH>() {

    private val items = ArrayList<Movie>()
    private val ids = HashSet<Int>()

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val title: TextView = v.findViewById(R.id.title)
        val year: TextView = v.findViewById(R.id.year)
    }

    fun clear() {
        items.clear()
        ids.clear()
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun add(list: List<Movie>) {
        val fresh = list.filter { ids.add(it.id) }
        if (fresh.isEmpty()) return
        val start = items.size
        items.addAll(fresh)
        notifyItemRangeInserted(start, fresh.size)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        v.setOnFocusChangeListener { view, hasFocus ->
            val s = if (hasFocus) 1.1f else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
            view.translationZ = if (hasFocus) 10f else 0f
        }
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.title.text = m.title
        h.year.text = if (m.rating > 0) "${m.year}  ★ ${"%.1f".format(m.rating)}" else m.year
        h.itemView.setOnClickListener { onClick(m) }
        if (m.posterPath != null) {
            Glide.with(h.poster)
                .load(Tmdb.IMG + "w342" + m.posterPath)
                .placeholder(R.drawable.poster_placeholder)
                .into(h.poster)
        } else {
            Glide.with(h.poster).clear(h.poster)
            h.poster.setImageResource(R.drawable.poster_placeholder)
        }
    }
}
