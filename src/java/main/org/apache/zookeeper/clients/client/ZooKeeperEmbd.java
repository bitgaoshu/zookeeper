package org.apache.zookeeper.clients.client;

import org.apache.zookeeper.clients.AsyncCallback;
import org.apache.zookeeper.clients.client.common.*;
import org.apache.zookeeper.exception.KeeperException;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.operation.multi.MultiTransactionRecord;
import org.apache.zookeeper.nodeMode.CreateMode;
import org.apache.zookeeper.operation.Op;
import org.apache.zookeeper.operation.OpResult;
import org.apache.zookeeper.operation.multi.Transaction;
import org.apache.zookeeper.watcher.Watcher;
import org.apache.zookeeper.watcher.WatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.List;


public class ZooKeeperEmbd extends ZooKeeper{

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperEmbd.class);

    @Override
    public void updateServerList(String connectString) throws IOException {
        super.updateServerList(connectString);
    }

    @Override
    public ZooKeeperSaslClient getSaslClient() {
        return super.getSaslClient();
    }

    @Override
    public ZKClientConfig getClientConfig() {
        return super.getClientConfig();
    }

    @Override
    public List<String> getDataWatches() {
        return super.getDataWatches();
    }

    @Override
    public List<String> getExistWatches() {
        return super.getExistWatches();
    }

    @Override
    public List<String> getChildWatches() {
        return super.getChildWatches();
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher) throws IOException {
        super(connectString, sessionTimeout, watcher);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, ZKClientConfig conf) throws IOException {
        super(connectString, sessionTimeout, watcher, conf);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly, HostProvider aHostProvider) throws IOException {
        super(connectString, sessionTimeout, watcher, canBeReadOnly, aHostProvider);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly, HostProvider aHostProvider, ZKClientConfig clientConfig) throws IOException {
        super(connectString, sessionTimeout, watcher, canBeReadOnly, aHostProvider, clientConfig);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly) throws IOException {
        super(connectString, sessionTimeout, watcher, canBeReadOnly);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, boolean canBeReadOnly, ZKClientConfig conf) throws IOException {
        super(connectString, sessionTimeout, watcher, canBeReadOnly, conf);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, long sessionId, byte[] sessionPasswd) throws IOException {
        super(connectString, sessionTimeout, watcher, sessionId, sessionPasswd);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, long sessionId, byte[] sessionPasswd, boolean canBeReadOnly, HostProvider aHostProvider) throws IOException {
        super(connectString, sessionTimeout, watcher, sessionId, sessionPasswd, canBeReadOnly, aHostProvider);
    }

    public ZooKeeperEmbd(String connectString, int sessionTimeout, Watcher watcher, long sessionId, byte[] sessionPasswd, boolean canBeReadOnly) throws IOException {
        super(connectString, sessionTimeout, watcher, sessionId, sessionPasswd, canBeReadOnly);
    }

    @Override
    protected ZKWatchManager defaultWatchManager() {
        return super.defaultWatchManager();
    }

    @Override
    public long getSessionId() {
        return super.getSessionId();
    }

    @Override
    public byte[] getSessionPasswd() {
        return super.getSessionPasswd();
    }

    @Override
    public int getSessionTimeout() {
        return super.getSessionTimeout();
    }

    @Override
    public void addAuthInfo(String scheme, byte[] auth) {
        super.addAuthInfo(scheme, auth);
    }

    @Override
    public synchronized void register(Watcher watcher) {
        super.register(watcher);
    }

    @Override
    public synchronized void close() throws InterruptedException {
        super.close();
    }

    @Override
    public boolean close(int waitForShutdownTimeoutMs) throws InterruptedException {
        return super.close(waitForShutdownTimeoutMs);
    }

    @Override
    public String create(String path, byte[] data, List<ACL> acl, CreateMode createMode) throws KeeperException, InterruptedException {
        return super.create(path, data, acl, createMode);
    }

    @Override
    public String create(String path, byte[] data, List<ACL> acl, CreateMode createMode, Stat stat) throws KeeperException, InterruptedException {
        return super.create(path, data, acl, createMode, stat);
    }

    @Override
    public String create(String path, byte[] data, List<ACL> acl, CreateMode createMode, Stat stat, long ttl) throws KeeperException, InterruptedException {
        return super.create(path, data, acl, createMode, stat, ttl);
    }

    @Override
    public void create(String path, byte[] data, List<ACL> acl, CreateMode createMode, AsyncCallback.StringCallback cb, Object ctx) {
        super.create(path, data, acl, createMode, cb, ctx);
    }

    @Override
    public void create(String path, byte[] data, List<ACL> acl, CreateMode createMode, AsyncCallback.Create2Callback cb, Object ctx) {
        super.create(path, data, acl, createMode, cb, ctx);
    }

    @Override
    public void create(String path, byte[] data, List<ACL> acl, CreateMode createMode, AsyncCallback.Create2Callback cb, Object ctx, long ttl) {
        super.create(path, data, acl, createMode, cb, ctx, ttl);
    }

    @Override
    public void delete(String path, int version) throws InterruptedException, KeeperException {
        super.delete(path, version);
    }

    @Override
    public List<OpResult> multi(Iterable<Op> ops) throws InterruptedException, KeeperException {
        return super.multi(ops);
    }

    @Override
    public void multi(Iterable<Op> ops, AsyncCallback.MultiCallback cb, Object ctx) {
        super.multi(ops, cb, ctx);
    }

    @Override
    protected void multiInternal(MultiTransactionRecord request, AsyncCallback.MultiCallback cb, Object ctx) {
        super.multiInternal(request, cb, ctx);
    }

    @Override
    protected List<OpResult> multiInternal(MultiTransactionRecord request) throws InterruptedException, KeeperException {
        return super.multiInternal(request);
    }

    @Override
    public Transaction transaction() {
        return super.transaction();
    }

    @Override
    public void delete(String path, int version, AsyncCallback.VoidCallback cb, Object ctx) {
        super.delete(path, version, cb, ctx);
    }

    @Override
    public Stat exists(String path, Watcher watcher) throws KeeperException, InterruptedException {
        return super.exists(path, watcher);
    }

    @Override
    public Stat exists(String path, boolean watch) throws KeeperException, InterruptedException {
        return super.exists(path, watch);
    }

    @Override
    public void exists(String path, Watcher watcher, AsyncCallback.StatCallback cb, Object ctx) {
        super.exists(path, watcher, cb, ctx);
    }

    @Override
    public void exists(String path, boolean watch, AsyncCallback.StatCallback cb, Object ctx) {
        super.exists(path, watch, cb, ctx);
    }

    @Override
    public byte[] getData(String path, Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        return super.getData(path, watcher, stat);
    }

    @Override
    public byte[] getData(String path, boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return super.getData(path, watch, stat);
    }

    @Override
    public void getData(String path, Watcher watcher, AsyncCallback.DataCallback cb, Object ctx) {
        super.getData(path, watcher, cb, ctx);
    }

    @Override
    public void getData(String path, boolean watch, AsyncCallback.DataCallback cb, Object ctx) {
        super.getData(path, watch, cb, ctx);
    }

    @Override
    public byte[] getConfig(Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        return super.getConfig(watcher, stat);
    }

    @Override
    public void getConfig(Watcher watcher, AsyncCallback.DataCallback cb, Object ctx) {
        super.getConfig(watcher, cb, ctx);
    }

    @Override
    public byte[] getConfig(boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return super.getConfig(watch, stat);
    }

    @Override
    public void getConfig(boolean watch, AsyncCallback.DataCallback cb, Object ctx) {
        super.getConfig(watch, cb, ctx);
    }

    @Override
    public Stat setData(String path, byte[] data, int version) throws KeeperException, InterruptedException {
        return super.setData(path, data, version);
    }

    @Override
    public void setData(String path, byte[] data, int version, AsyncCallback.StatCallback cb, Object ctx) {
        super.setData(path, data, version, cb, ctx);
    }

    @Override
    public List<ACL> getACL(String path, Stat stat) throws KeeperException, InterruptedException {
        return super.getACL(path, stat);
    }

    @Override
    public void getACL(String path, Stat stat, AsyncCallback.ACLCallback cb, Object ctx) {
        super.getACL(path, stat, cb, ctx);
    }

    @Override
    public Stat setACL(String path, List<ACL> acl, int aclVersion) throws KeeperException, InterruptedException {
        return super.setACL(path, acl, aclVersion);
    }

    @Override
    public void setACL(String path, List<ACL> acl, int version, AsyncCallback.StatCallback cb, Object ctx) {
        super.setACL(path, acl, version, cb, ctx);
    }

    @Override
    public List<String> getChildren(String path, Watcher watcher) throws KeeperException, InterruptedException {
        return super.getChildren(path, watcher);
    }

    @Override
    public List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException {
        return super.getChildren(path, watch);
    }

    @Override
    public void getChildren(String path, Watcher watcher, AsyncCallback.ChildrenCallback cb, Object ctx) {
        super.getChildren(path, watcher, cb, ctx);
    }

    @Override
    public void getChildren(String path, boolean watch, AsyncCallback.ChildrenCallback cb, Object ctx) {
        super.getChildren(path, watch, cb, ctx);
    }

    @Override
    public List<String> getChildren(String path, Watcher watcher, Stat stat) throws KeeperException, InterruptedException {
        return super.getChildren(path, watcher, stat);
    }

    @Override
    public List<String> getChildren(String path, boolean watch, Stat stat) throws KeeperException, InterruptedException {
        return super.getChildren(path, watch, stat);
    }

    @Override
    public void getChildren(String path, Watcher watcher, AsyncCallback.Children2Callback cb, Object ctx) {
        super.getChildren(path, watcher, cb, ctx);
    }

    @Override
    public void getChildren(String path, boolean watch, AsyncCallback.Children2Callback cb, Object ctx) {
        super.getChildren(path, watch, cb, ctx);
    }

    @Override
    public void sync(String path, AsyncCallback.VoidCallback cb, Object ctx) {
        super.sync(path, cb, ctx);
    }

    @Override
    public void removeWatches(String path, Watcher watcher, WatcherType watcherType, boolean local) throws InterruptedException, KeeperException {
        super.removeWatches(path, watcher, watcherType, local);
    }

    @Override
    public void removeWatches(String path, Watcher watcher, WatcherType watcherType, boolean local, AsyncCallback.VoidCallback cb, Object ctx) {
        super.removeWatches(path, watcher, watcherType, local, cb, ctx);
    }

    @Override
    public void removeAllWatches(String path, WatcherType watcherType, boolean local) throws InterruptedException, KeeperException {
        super.removeAllWatches(path, watcherType, local);
    }

    @Override
    public void removeAllWatches(String path, WatcherType watcherType, boolean local, AsyncCallback.VoidCallback cb, Object ctx) {
        super.removeAllWatches(path, watcherType, local, cb, ctx);
    }

    @Override
    public States getState() {
        return super.getState();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    protected boolean testableWaitForShutdown(int wait) throws InterruptedException {
        return super.testableWaitForShutdown(wait);
    }

    @Override
    protected SocketAddress testableRemoteSocketAddress() {
        return super.testableRemoteSocketAddress();
    }

    @Override
    protected SocketAddress testableLocalSocketAddress() {
        return super.testableLocalSocketAddress();
    }
}
