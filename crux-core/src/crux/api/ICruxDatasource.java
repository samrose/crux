package crux.api;

import java.io.Closeable;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import clojure.lang.Keyword;

/**
 * Represents the database as of a specific valid and
 * transaction time.
 */
public interface ICruxDatasource extends Closeable {
    /**
     * Returns the document map for an entity.
     *
     * @param eid an object that can be coerced into an entity id.
     * @return    the entity document map.
     */
    public Map<Keyword,Object> entity(Object eid);

    /**
     * Returns the document map for an entity using an existing snapshot.
     *
     * @param snapshot a snapshot from {@link #newSnapshot()}.
     * @param eid an object that can be coerced into an entity id.
     * @return    the entity document map.
     */
    @Deprecated
    public Map<Keyword,Object> entity(Closeable snapshot, Object eid);

    /**
     * Returns the transaction details for an entity. Details
     * include tx-id and tx-time.
     *
     * @param eid an object that can be coerced into an entity id.
     * @return    the entity transaction details.
     */
    public Map<Keyword,?> entityTx(Object eid);

    /**
     * Returns a new snapshot allowing for lazy query results in a
     * try-with-resources block using {@link #q(Closeable snapshot,
     * Object query)}. Can also be used for {@link
     * #historyAscending(Closeable snapshot, Object eid)} and {@link
     * #historyDescending(Closeable snapshot, Object eid)}
     *
     * @return an implementation specific snapshot
     */
    @Deprecated
    public Closeable newSnapshot();

    /**
     * Queries the db.
     *
     * @param query the query in map, vector or string form.
     * @return      a set or vector of result tuples.
     * @deprecated  renamed to {@link #query(Object)}
     */
    @Deprecated
    public Collection<List<?>> q(Object query);

    /**
     * Queries the db.
     *
     * @param query the query in map, vector or string form.
     * @return      a set or vector of result tuples.
     */
    public Collection<List<?>> query(Object query);

    /**
     * Queries the db lazily.
     *
     * @param snapshot a snapshot from {@link #newSnapshot()}.
     * @param query    the query in map, vector or string form.
     * @return         a lazy sequence of result tuples.
     */
    @Deprecated
    public Iterable<List<?>> q(Closeable snapshot, Object query);

    /**
     * Queries the db lazily.
     *
     * @param query the query in map, vector or string form.
     * @return      a cursor of result tuples.
     */
    public ICursor<List<?>> openQuery(Object query);

    /**
     * Retrieves entity history in chronological order from and
     * including the valid time of the db while respecting
     * transaction time. Includes the documents.
     *
     * @param eid      an object that can be coerced into an entity id.
     * @return         the history of the given entity.
     */
    public List<Map<Keyword,?>> historyAscending(Object eid);

    /**
     * Retrieves entity history lazily in chronological order from and
     * including the valid time of the db while respecting
     * transaction time. Includes the documents.
     *
     * @param snapshot a snapshot from {@link #newSnapshot()}.
     * @param eid      an object that can be coerced into an entity id.
     * @return         a lazy sequence of history.
     */
    @Deprecated
    public Iterable<Map<Keyword,?>> historyAscending(Closeable snapshot, Object eid);

    /**
     * Retrieves entity history lazily in chronological order from and
     * including the valid time of the db while respecting
     * transaction time. Includes the documents.
     *
     * @param eid      an object that can be coerced into an entity id.
     * @return         a stream of history.
     */
    @Deprecated
    public ICursor<Map<Keyword,?>> openHistoryAscending(Object eid);

    /**
     * Retrieves entity history in reverse chronological order
     * from and including the valid time of the db while respecting
     * transaction time. Includes the documents.
     *
     * @param eid      an object that can be coerced into an entity id.
     * @return         the history of the given entity.
     */
    @Deprecated
    public List<Map<Keyword,?>> historyDescending(Object eid);

    /**
     * Retrieves entity history lazily in reverse chronological order
     * from and including the valid time of the db while respecting
     * transaction time. Includes the documents.
     *
     * @param snapshot a snapshot from {@link #newSnapshot()}.
     * @param eid      an object that can be coerced into an entity id.
     * @return         a lazy sequence of history.
     */
    @Deprecated
    public Iterable<Map<Keyword,?>> historyDescending(Closeable snapshot, Object eid);

    /**
     * Retrieves entity history lazily in reverse chronological order
     * from and including the valid time of the db while respecting
     * transaction time. Includes the documents.
     *
     * @param eid      an object that can be coerced into an entity id.
     * @return         a stream of history.
     */
    @Deprecated
    public ICursor<Map<Keyword,?>> openHistoryDescending(Object eid);

    /**
     * Eagerly retrieves entity history for the given entity.
     *
     * Each entry in the result contains the following keys:
     * * `:crux.db/valid-time`,
     * * `:crux.db/tx-time`,
     * * `:crux.tx/tx-id`,
     * * `:crux.db/content-hash`
     * * `:crux.db/doc` (if {@link HistoryOptions#withDocs(boolean) withDocs} is set on the options).
     *
     * If {@link HistoryOptions#withCorrections(boolean) withCorrections} is set
     * on the options, bitemporal corrections are also included in the sequence,
     * sorted first by valid-time, then transaction-time.
     *
     * No matter what `start` and `end` parameters you specify, you won't receive
     * results later than the valid-time and transact-time of this DB value.
     *
     * @param eid The entity id to return history for
     * @return an eagerly-evaluated sequence of changes to the given entity.
     */
    public List<Map<Keyword, ?>> entityHistory(Object eid, HistoryOptions options);

    /**
     * Lazily retrieves entity history for the given entity.
     * Don't forget to close the cursor when you've consumed enough history!
     *
     * @see #entityHistory(Object, HistoryOptions)
     * @param eid The entity id to return history for
     * @return a cursor of changes to the given entity.
     */
    public ICursor<Map<Keyword, ?>> openEntityHistory(Object eid, HistoryOptions options);

    /**
     * The valid time of this db.
     * If valid time wasn't specified at the moment of the db value retrieval
     * then valid time will be time of the latest transaction.
     *
     * @return the valid time of this db.
     */
    public Date validTime();

    /**
     * @return the time of the latest transaction applied to this db value.
     * If a tx time was specified when db value was acquired then returns
     * the specified time.
     */
    public Date transactionTime();
}
