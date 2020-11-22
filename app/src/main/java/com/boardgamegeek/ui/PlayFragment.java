package com.boardgamegeek.ui;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.boardgamegeek.BggApplication;
import com.boardgamegeek.R;
import com.boardgamegeek.events.PlayDeletedEvent;
import com.boardgamegeek.events.PlaySentEvent;
import com.boardgamegeek.extensions.ActivityUtils;
import com.boardgamegeek.extensions.PreferenceUtils;
import com.boardgamegeek.extensions.SwipeRefreshLayoutUtils;
import com.boardgamegeek.extensions.TaskUtils;
import com.boardgamegeek.extensions.TextViewUtils;
import com.boardgamegeek.model.Play;
import com.boardgamegeek.model.Player;
import com.boardgamegeek.model.builder.PlayBuilder;
import com.boardgamegeek.model.persister.PlayPersister;
import com.boardgamegeek.provider.BggContract;
import com.boardgamegeek.provider.BggContract.Plays;
import com.boardgamegeek.tasks.sync.SyncPlaysByGameTask;
import com.boardgamegeek.ui.adapter.PlayPlayerAdapter;
import com.boardgamegeek.ui.widget.ContentLoadingProgressBar;
import com.boardgamegeek.ui.widget.TimestampView;
import com.boardgamegeek.util.DateTimeUtils;
import com.boardgamegeek.util.DialogUtils;
import com.boardgamegeek.util.ImageUtils;
import com.boardgamegeek.util.ImageUtils.Callback;
import com.boardgamegeek.util.NotificationUtils;
import com.boardgamegeek.util.UIUtils;
import com.boardgamegeek.util.XmlApiMarkupConverter;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.analytics.FirebaseAnalytics.Event;
import com.google.firebase.analytics.FirebaseAnalytics.Param;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.app.LoaderManager.LoaderCallbacks;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.palette.graphics.Palette;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;

public class PlayFragment extends Fragment implements LoaderCallbacks<Cursor>, OnRefreshListener {
	private static final String KEY_ID = "ID";
	private static final String KEY_GAME_ID = "GAME_ID";
	private static final String KEY_GAME_NAME = "GAME_NAME";
	private static final String KEY_IMAGE_URL = "IMAGE_URL";
	private static final String KEY_THUMBNAIL_URL = "THUMBNAIL_URL";
	private static final String KEY_HERO_IMAGE_URL = "HERO_IMAGE_URL";
	private static final String KEY_HAS_BEEN_NOTIFIED = "HAS_BEEN_NOTIFIED";
	private static final int AGE_IN_DAYS_TO_REFRESH = 7;
	private static final int PLAY_QUERY_TOKEN = 0x01;
	private static final int PLAYER_QUERY_TOKEN = 0x02;

	private long internalId = BggContract.INVALID_ID;
	private Play play = new Play();
	private String thumbnailUrl;
	private String imageUrl;
	private String heroImageUrl;
	private boolean isRefreshing;
	private SharedPreferences prefs;
	private XmlApiMarkupConverter markupConverter;

	private Unbinder unbinder;
	@BindView(R.id.recyclerView) RecyclerView playersView;
	@BindView(R.id.swipeRefreshLayout) SwipeRefreshLayout swipeRefreshLayout;
	@BindView(R.id.progressBar) ContentLoadingProgressBar progressBar;
	@BindView(R.id.listContainer) View listContainer;
	@BindView(R.id.emptyView) TextView emptyView;
	@BindView(R.id.thumbnailView) ImageView thumbnailView;
	@BindView(R.id.gameNameView) TextView gameNameView;
	@BindView(R.id.dateView) TextView dateView;
	@BindView(R.id.quantityView) TextView quantityView;
	@BindView(R.id.lengthContainer) View lengthContainer;
	@BindView(R.id.lengthView) TextView lengthView;
	@BindView(R.id.timerContainer) View timerContainer;
	@BindView(R.id.timerView) Chronometer timerView;
	@BindView(R.id.locationContainer) View locationContainer;
	@BindView(R.id.locationView) TextView locationView;
	@BindView(R.id.incompleteView) View incompleteView;
	@BindView(R.id.noWinStatsView) View noWinStatsView;
	@BindView(R.id.commentsView) TextView commentsView;
	@BindView(R.id.commentsLabel) View commentsLabel;
	@BindView(R.id.playersLabel) View playersLabel;
	@BindView(R.id.playIdView) TextView playIdView;
	@BindView(R.id.pendingTimestampView) TimestampView pendingTimestampView;
	@BindView(R.id.dirtyTimestampView) TimestampView dirtyTimestampView;
	@BindView(R.id.syncTimestampView) TimestampView syncTimestampView;
	private PlayPlayerAdapter adapter;
	private boolean hasBeenNotified;
	private FirebaseAnalytics firebaseAnalytics;

	public static PlayFragment newInstance(long internalId, int gameId, String gameName, String imageUrl, String thumbnailUrl, String heroImageUrl) {
		Bundle args = new Bundle();
		args.putLong(KEY_ID, internalId);
		args.putInt(KEY_GAME_ID, gameId);
		args.putString(KEY_GAME_NAME, gameName);
		args.putString(KEY_IMAGE_URL, imageUrl);
		args.putString(KEY_THUMBNAIL_URL, thumbnailUrl);
		args.putString(KEY_HERO_IMAGE_URL, heroImageUrl);
		PlayFragment fragment = new PlayFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		readBundle(getArguments());
		firebaseAnalytics = FirebaseAnalytics.getInstance(requireContext());
		if (savedInstanceState != null) {
			hasBeenNotified = savedInstanceState.getBoolean(KEY_HAS_BEEN_NOTIFIED);
		}
		setHasOptionsMenu(true);
		prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
		markupConverter = new XmlApiMarkupConverter(requireContext());
	}

	private void readBundle(@Nullable Bundle bundle) {
		if (bundle == null) return;
		internalId = bundle.getLong(KEY_ID, BggContract.INVALID_ID);
		play = new Play(bundle.getInt(KEY_GAME_ID, BggContract.INVALID_ID), bundle.getString(KEY_GAME_NAME));
		thumbnailUrl = bundle.getString(KEY_THUMBNAIL_URL);
		imageUrl = bundle.getString(KEY_IMAGE_URL);
		heroImageUrl = bundle.getString(KEY_HERO_IMAGE_URL);

		if (thumbnailUrl == null) thumbnailUrl = "";
		if (imageUrl == null) imageUrl = "";
		if (heroImageUrl == null) heroImageUrl = "";
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_play, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		unbinder = ButterKnife.bind(this, view);

		if (swipeRefreshLayout != null) {
			SwipeRefreshLayoutUtils.setBggColors(swipeRefreshLayout);
			swipeRefreshLayout.setOnRefreshListener(this);
		}

		adapter = new PlayPlayerAdapter(play);
		playersView.setAdapter(adapter);

		LoaderManager.getInstance(this).restartLoader(PLAY_QUERY_TOKEN, null, this);
	}

	@Override
	public void onStart() {
		super.onStart();
		EventBus.getDefault().register(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (play != null && play.hasStarted()) {
			showNotification();
			hasBeenNotified = true;
		}
	}

	@Override
	public void onStop() {
		EventBus.getDefault().unregister(this);
		super.onStop();
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (unbinder != null) unbinder.unbind();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean(KEY_HAS_BEEN_NOTIFIED, hasBeenNotified);
	}

	@Override
	public void onCreateOptionsMenu(@NotNull Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.play, menu);
	}

	@Override
	public void onPrepareOptionsMenu(@NotNull Menu menu) {
		UIUtils.showMenuItem(menu, R.id.menu_send, play.dirtyTimestamp > 0);
		UIUtils.showMenuItem(menu, R.id.menu_discard, play.playId > 0 && play.dirtyTimestamp > 0);
		UIUtils.enableMenuItem(menu, R.id.menu_share, play.playId > 0);
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_discard:
				DialogUtils.createDiscardDialog(getActivity(), R.string.play, false, false, () -> {
					play.dirtyTimestamp = 0;
					play.updateTimestamp = 0;
					play.deleteTimestamp = 0;
					save("Discard");
				}).show();
				return true;
			case R.id.menu_edit:
				logDataManipulationAction("Edit");
				LogPlayActivity.editPlay(getActivity(), internalId, play.gameId, play.gameName, thumbnailUrl, imageUrl, heroImageUrl);
				return true;
			case R.id.menu_send:
				play.updateTimestamp = System.currentTimeMillis();
				save("Save");
				EventBus.getDefault().post(new PlaySentEvent());
				return true;
			case R.id.menu_delete: {
				DialogUtils.createThemedBuilder(getContext())
					.setMessage(R.string.are_you_sure_delete_play)
					.setPositiveButton(R.string.delete, (dialog, id) -> {
						if (play.hasStarted()) cancelNotification();
						play.end(); // this prevents the timer from reappearing
						play.deleteTimestamp = System.currentTimeMillis();
						save("Delete");
						EventBus.getDefault().post(new PlayDeletedEvent());
					})
					.setNegativeButton(R.string.cancel, null)
					.setCancelable(true)
					.show();
				return true;
			}
			case R.id.menu_rematch:
				logDataManipulationAction("Rematch");
				LogPlayActivity.rematch(getContext(), internalId, play.gameId, play.gameName, thumbnailUrl, imageUrl, heroImageUrl);
				getActivity().finish(); // don't want to show the "old" play upon return
				return true;
			case R.id.menu_change_game:
				logDataManipulationAction("ChangeGame");
				startActivity(CollectionActivity.createIntentForGameChange(requireContext(), internalId));
				getActivity().finish(); // don't want to show the "old" play upon return
				return true;
			case R.id.menu_share:
				ActivityUtils.share(getActivity(), play.toShortDescription(requireContext()), play.toLongDescription(requireContext()), R.string.share_play_title);
				Bundle bundle = new Bundle();
				bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "Play");
				bundle.putString(Param.ITEM_ID, String.valueOf(play.playId));
				bundle.putString(Param.ITEM_NAME, play.toShortDescription(requireContext()));
				firebaseAnalytics.logEvent(Event.SHARE, bundle);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void logDataManipulationAction(String action) {
		Bundle bundle = new Bundle();
		bundle.putString(Param.CONTENT_TYPE, "Play");
		bundle.putString("Action", action);
		bundle.putString("GameName", play.gameName);
		firebaseAnalytics.logEvent("DataManipulation", bundle);
	}

	@Override
	public void onRefresh() {
		triggerRefresh();
	}

	@Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
	public void onEvent(SyncPlaysByGameTask.CompletedEvent event) {
		if (play != null && event.getGameId() == play.gameId) {
			if (!TextUtils.isEmpty(event.getErrorMessage()) && PreferenceUtils.getSyncShowErrors(prefs)) {
				// TODO: 3/30/17 change to a snackbar (will need to change from a ListFragment)
				Toast.makeText(getContext(), event.getErrorMessage(), Toast.LENGTH_LONG).show();
			}
			updateRefreshStatus(false);
		}
	}

	private void updateRefreshStatus(final boolean value) {
		isRefreshing = value;
		if (swipeRefreshLayout != null) {
			swipeRefreshLayout.post(() -> {
				if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(isRefreshing);
			});
		}
	}

	@OnClick(R.id.headerContainer)
	void viewGame() {
		GameActivity.start(requireContext(), play.gameId, play.gameName);
	}

	@OnClick(R.id.timerEndButton)
	void onTimerClick() {
		LogPlayActivity.endPlay(getContext(), internalId, play.gameId, play.gameName, thumbnailUrl, imageUrl, heroImageUrl);
	}

	@Override
	public @NotNull Loader<Cursor> onCreateLoader(int id, Bundle data) {
		CursorLoader loader = null;
		switch (id) {
			case PLAY_QUERY_TOKEN:
				loader = new CursorLoader(requireContext(), Plays.buildPlayUri(internalId), PlayBuilder.PLAY_PROJECTION, null, null, null);
				break;
			case PLAYER_QUERY_TOKEN:
				loader = new CursorLoader(requireContext(), Plays.buildPlayerUri(internalId), PlayBuilder.PLAYER_PROJECTION, null, null, null);
				break;
		}
		if (loader != null) {
			loader.setUpdateThrottle(1000);
		}
		return loader;
	}

	@Override
	public void onLoadFinished(@NotNull Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) {
			return;
		}

		switch (loader.getId()) {
			case PLAY_QUERY_TOKEN:
				if (onPlayQueryComplete(cursor)) {
					showList();
				}
				break;
			case PLAYER_QUERY_TOKEN:
				PlayBuilder.addPlayers(cursor, play);
				playersLabel.setVisibility(play.getPlayers().size() == 0 ? View.GONE : View.VISIBLE);
				adapter.replace(play);
				maybeShowNotification();
				showList();
				break;
			default:
				if (cursor != null) {
					cursor.close();
				}
				break;
		}
	}

	private void showList() {
		progressBar.hide();
		listContainer.setVisibility(View.VISIBLE);
	}

	@Override
	public void onLoaderReset(@NotNull Loader<Cursor> loader) {
	}

	/**
	 * @return true if the we're done loading
	 */
	private boolean onPlayQueryComplete(Cursor cursor) {
		if (cursor == null || !cursor.moveToFirst()) {
			emptyView.setText(getResources().getString(R.string.empty_play, String.valueOf(internalId)));
			emptyView.setVisibility(View.VISIBLE);
			return true;
		}
		playersView.setVisibility(View.VISIBLE);
		emptyView.setVisibility(View.GONE);

		ImageUtils.safelyLoadImage(thumbnailView, imageUrl, thumbnailUrl, heroImageUrl, new Callback() {
			@Override
			public void onSuccessfulImageLoad(Palette palette) {
				if (gameNameView != null && isAdded()) {
					gameNameView.setBackgroundResource(R.color.black_overlay_light);
				}
			}

			@Override
			public void onFailedImageLoad() {
			}
		});

		List<Player> players = play.getPlayers();
		play = PlayBuilder.fromCursor(cursor);
		play.setPlayers(players);

		gameNameView.setText(play.gameName);

		dateView.setText(play.getDateForDisplay(getActivity()));

		quantityView.setText(getResources().getQuantityString(R.plurals.times_suffix, play.quantity, play.quantity));
		quantityView.setVisibility((play.quantity == 1) ? View.GONE : View.VISIBLE);

		if (play.length > 0) {
			lengthContainer.setVisibility(View.VISIBLE);
			lengthView.setText(DateTimeUtils.describeMinutes(requireContext(), play.length));
			lengthView.setVisibility(View.VISIBLE);
			timerContainer.setVisibility(View.GONE);
			timerView.stop();
		} else if (play.hasStarted()) {
			lengthContainer.setVisibility(View.VISIBLE);
			lengthView.setVisibility(View.GONE);
			timerContainer.setVisibility(View.VISIBLE);
			UIUtils.startTimerWithSystemTime(timerView, play.startTime);
		} else {
			lengthContainer.setVisibility(View.GONE);
		}

		locationView.setText(play.location);
		locationContainer.setVisibility(TextUtils.isEmpty(play.location) ? View.GONE : View.VISIBLE);

		incompleteView.setVisibility(play.incomplete ? View.VISIBLE : View.GONE);
		noWinStatsView.setVisibility(play.noWinStats ? View.VISIBLE : View.GONE);

		TextViewUtils.setTextMaybeHtml(commentsView, markupConverter.toHtml(play.comments));
		commentsView.setVisibility(TextUtils.isEmpty(play.comments) ? View.GONE : View.VISIBLE);
		commentsLabel.setVisibility(TextUtils.isEmpty(play.comments) ? View.GONE : View.VISIBLE);

		if (play.deleteTimestamp > 0) {
			pendingTimestampView.setVisibility(View.VISIBLE);
			pendingTimestampView.setFormat(getString(R.string.delete_pending_prefix));
			pendingTimestampView.setTimestamp(play.deleteTimestamp);
		} else if (play.updateTimestamp > 0) {
			pendingTimestampView.setVisibility(View.VISIBLE);
			pendingTimestampView.setFormat(getString(R.string.update_pending_prefix));
			pendingTimestampView.setTimestamp(play.updateTimestamp);
		} else {
			pendingTimestampView.setVisibility(View.GONE);
		}

		if (play.dirtyTimestamp > 0) {
			dirtyTimestampView.setFormat(getString(play.playId > 0 ? R.string.editing_prefix : R.string.draft_prefix));
			dirtyTimestampView.setTimestamp(play.dirtyTimestamp);
		} else {
			dirtyTimestampView.setVisibility(View.GONE);
		}

		if (play.playId > 0) {
			playIdView.setText(getResources().getString(R.string.play_id_prefix, String.valueOf(play.playId)));
		}

		syncTimestampView.setTimestamp(play.syncTimestamp);

		getActivity().invalidateOptionsMenu();
		LoaderManager.getInstance(this).restartLoader(PLAYER_QUERY_TOKEN, null, this);

		if (play.playId > 0 &&
			(play.syncTimestamp == 0 || DateTimeUtils.howManyDaysOld(play.syncTimestamp) > AGE_IN_DAYS_TO_REFRESH)) {
			triggerRefresh();
		}

		return false;
	}

	private void maybeShowNotification() {
		if (play.hasStarted()) {
			showNotification();
		} else if (hasBeenNotified) {
			cancelNotification();
		}
	}

	private void showNotification() {
		NotificationUtils.launchPlayingNotification(getActivity(), internalId, play, thumbnailUrl, imageUrl, heroImageUrl);
	}

	private void cancelNotification() {
		NotificationUtils.cancel(getActivity(), NotificationUtils.TAG_PLAY_TIMER, internalId);
	}

	private void triggerRefresh() {
		if (!isRefreshing) {
			TaskUtils.executeAsyncTask(new SyncPlaysByGameTask((BggApplication) getActivity().getApplication(), play.gameId));
			updateRefreshStatus(true);
		}
	}

	private void save(String action) {
		logDataManipulationAction(TextUtils.isEmpty(action) ? "Save" : action);
		new PlayPersister(requireContext()).save(play, internalId, false);
		triggerRefresh();
	}
}
