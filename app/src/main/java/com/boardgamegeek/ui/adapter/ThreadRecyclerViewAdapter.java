package com.boardgamegeek.ui.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.model.Article;
import com.boardgamegeek.util.ActivityUtils;
import com.boardgamegeek.util.DateTimeUtils;
import com.boardgamegeek.util.UIUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import hugo.weaving.DebugLog;

public class ThreadRecyclerViewAdapter extends RecyclerView.Adapter<ThreadRecyclerViewAdapter.ArticleViewHolder> {
	private final List<Article> articles;
	private final LayoutInflater inflater;
	private final Resources resources;

	public ThreadRecyclerViewAdapter(Context context, List<Article> articles) {
		this.articles = articles;
		inflater = LayoutInflater.from(context);
		resources = context.getResources();
		setHasStableIds(true);
	}

	@Override
	public ArticleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		return new ArticleViewHolder(inflater.inflate(R.layout.row_thread_article, parent, false));
	}

	@Override
	public void onBindViewHolder(ArticleViewHolder holder, int position) {
		holder.bind(articles.get(position));
	}

	@Override
	public int getItemCount() {
		return articles == null ? 0 : articles.size();
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	public class ArticleViewHolder extends RecyclerView.ViewHolder {
		@BindView(R.id.username) TextView usernameView;
		@BindView(R.id.edit_date) TextView editDateView;
		@BindView(R.id.body) TextView bodyView;
		@BindView(R.id.view_button) View viewButton;

		@DebugLog
		public ArticleViewHolder(View itemView) {
			super(itemView);
			ButterKnife.bind(this, itemView);
		}

		public void bind(Article article) {
			if (article == null) return;

			Context context = itemView.getContext();
			usernameView.setText(article.username);
			int dateRes = R.string.posted_prefix;
			if (article.getNumberOfEdits() > 0) {
				dateRes = R.string.edited_prefix;
			}
			editDateView.setText(context.getString(dateRes, DateTimeUtils.formatForumDate(context, article.editDate())));
			UIUtils.setTextMaybeHtml(bodyView, article.body);
			Bundle bundle = new Bundle();
			bundle.putString(ActivityUtils.KEY_USER, article.username);
			bundle.putLong(ActivityUtils.KEY_POST_DATE, article.postDate());
			bundle.putLong(ActivityUtils.KEY_EDIT_DATE, article.editDate());
			bundle.putInt(ActivityUtils.KEY_EDIT_COUNT, article.getNumberOfEdits());
			bundle.putString(ActivityUtils.KEY_BODY, article.body);
			bundle.putString(ActivityUtils.KEY_LINK, article.link);
			viewButton.setTag(bundle);
		}
	}
}
