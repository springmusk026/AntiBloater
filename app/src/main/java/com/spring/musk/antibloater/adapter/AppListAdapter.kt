package com.spring.musk.antibloater.adapter

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import com.spring.musk.antibloater.R
import java.util.Locale

class AppListAdapter(context: Context, private val appsList: List<ApplicationInfo>) :
    ArrayAdapter<ApplicationInfo>(context, R.layout.list_item_app, appsList) {

    private val checkedApps = mutableSetOf<ApplicationInfo>()
    private var filteredAppsList = appsList.toMutableList()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_app, parent, false)
        val appInfo = filteredAppsList[position]

        val appNameTextView: TextView = itemView.findViewById(R.id.appName)
        val appIconImageView: ImageView = itemView.findViewById(R.id.appIcon)
        val appCheckbox: CheckBox = itemView.findViewById(R.id.appCheckbox)

        appNameTextView.text = context.packageManager.getApplicationLabel(appInfo).toString()
        appIconImageView.setImageDrawable(context.packageManager.getApplicationIcon(appInfo))

        appCheckbox.setOnCheckedChangeListener(null)
        appCheckbox.isChecked = checkedApps.contains(appInfo)

        appCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkedApps.add(appInfo)
            } else {
                checkedApps.remove(appInfo)
            }
        }

        return itemView
    }

    override fun getCount(): Int {
        return filteredAppsList.size
    }

    override fun getItem(position: Int): ApplicationInfo? {
        return filteredAppsList[position]
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val queryString = constraint.toString().toLowerCase(Locale.getDefault())
                val filterResults = FilterResults()

                val filteredList = if (queryString.isEmpty()) {
                    appsList.toMutableList()
                } else {
                    appsList.filter { appInfo ->
                        context.packageManager.getApplicationLabel(appInfo)
                            .toString().toLowerCase(Locale.getDefault()).contains(queryString) ||
                                appInfo.packageName.toLowerCase(Locale.getDefault()).contains(queryString)
                    }.toMutableList()
                }

                filterResults.values = filteredList
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredAppsList = results?.values as MutableList<ApplicationInfo>
                notifyDataSetChanged()
            }
        }
    }

    fun getSelectedApps(): Set<ApplicationInfo> {
        return checkedApps
    }
}
