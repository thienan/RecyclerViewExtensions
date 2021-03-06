package io.doist.recyclerviewext.animations;

import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Adds functionality to animate differences between an adapter's data set and a new one.
 * In this variant, the differences are calculated in a background thread.
 *
 * When using this class, *never* update your data set or use any of the {@code notify*} methods between the call to
 * {@link #diffDataSet(AsyncCallback)} and the call to {@link AsyncCallback#submit()}.
 *
 * @see DataSetDiffer
 */
public class AsyncDataSetDiffer {
    private RecyclerView.Adapter adapter;
    private DataSetDiffer dataSetDiffer;

    private Handler handler;
    private ThreadPoolExecutor executor;

    /**
     * @param adapter  Adapter with which this data set differ is associated.
     * @param callback Callback that provides information about the items set in the adapter.
     */
    public AsyncDataSetDiffer(RecyclerView.Adapter adapter, DataSetDiffer.Callback callback) {
        if (!adapter.hasStableIds()) {
            adapter.setHasStableIds(true);
        }
        this.adapter = adapter;
        dataSetDiffer = new DataSetDiffer(adapter, callback);
        handler = new Handler(Looper.getMainLooper());
        executor = new ThreadPoolExecutor(
                0, 1, 5, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.allowCoreThreadTimeOut(true);
    }

    /**
     * Analyzes the data set in the background using the supplied {@link AsyncCallback}.
     * When done, calls {@link AsyncCallback#submit()} to ensure the data set is updated and triggers all necessary
     * {@code notify} calls.
     *
     * @param callback Callback that provides information about the items *to be set* in the adapter.
     *                 Note the difference between this callback and the one passed in the constructor.
     */
    public void diffDataSet(final AsyncCallback callback) {
        // Pause adapter monitoring to avoid double counting changes.
        dataSetDiffer.stopObservingItems();

        // Diff data set in the background, apply the changes and notify in the UI thread.
        executor.execute(new Runnable() {
            @Override
            public void run() {
                final OpDiffHandler opDiffHandler = new OpDiffHandler();
                dataSetDiffer.diffDataSet(opDiffHandler, callback);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.submit();
                        opDiffHandler.notify(adapter);

                        // Resume adapter monitoring.
                        dataSetDiffer.startObservingItems();
                    }
                });
            }
        });
    }

    /**
     * Callback for asynchronously calculating the difference between the current data set and a new one.
     *
     * @see DataSetDiffer.Callback
     */
    public interface AsyncCallback extends DataSetDiffer.Callback {
        /**
         * Submit the data set changes, ie. set your adapter collection to the new collection.
         */
        void submit();
    }
}
