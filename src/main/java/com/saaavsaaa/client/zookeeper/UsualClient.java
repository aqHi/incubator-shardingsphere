package com.saaavsaaa.client.zookeeper;
/**
 * Created by aaa on 18-4-18.
 */

import com.saaavsaaa.client.untils.Listener;
import com.saaavsaaa.client.untils.PathUtil;
import com.saaavsaaa.client.untils.StringUtil;
import org.apache.zookeeper.*;

import java.util.List;
import java.util.Stack;

/*
* cache
* todo Sequential
*/
public class UsualClient extends BaseClient {
    private final boolean watched = true; //false
    private boolean watchRegistered = false;
    UsualClient(String servers, int sessionTimeoutMilliseconds) {
        super(servers, sessionTimeoutMilliseconds);
    }

    public void createNamespace() throws KeeperException, InterruptedException {
        if (checkExists(rootNode)){
            return;
        }
        zooKeeper.create(rootNode, new byte[0], authorities, CreateMode.PERSISTENT);
    }
    
    public void deleteNamespace() throws KeeperException, InterruptedException {
        zooKeeper.delete(rootNode, VERSION);
    }
    
    public String getDataString(final String key) throws KeeperException, InterruptedException {
        return new String(getData(key));
    }

    public byte[] getData(final String key) throws KeeperException, InterruptedException {
        return zooKeeper.getData(PathUtil.getRealPath(rootNode, key), watched, null);
    }
    
    public void getData(final String key, final AsyncCallback.DataCallback callback, final Object ctx) throws KeeperException, InterruptedException {
        zooKeeper.getData(PathUtil.getRealPath(rootNode, key), watched, callback, ctx);
    }
    
    public boolean checkExists(final String key) throws KeeperException, InterruptedException {
        return null != zooKeeper.exists(PathUtil.getRealPath(rootNode, key), watched);
    }
    
    public boolean checkExists(final String key, final Watcher watcher) throws KeeperException, InterruptedException {
        return null != zooKeeper.exists(PathUtil.getRealPath(rootNode, key), watcher);
    }
    
    public List<String> getChildren(final String key) throws KeeperException, InterruptedException {
        return zooKeeper.getChildren(PathUtil.getRealPath(rootNode, key), watched);
    }
    
    public void createCurrentOnly(final String key, final String value, final CreateMode createMode) throws KeeperException, InterruptedException {
        zooKeeper.create(PathUtil.getRealPath(rootNode, key), value.getBytes(StringUtil.UTF_8), authorities, createMode);
    }
    
    /*
    * closed beta
    */
    public void createAllNeedPath(final String key, final String value, final CreateMode createMode) throws KeeperException, InterruptedException {
        if (key.indexOf(PathUtil.PATH_SEPARATOR) < -1){
            this.createCurrentOnly(key, value, createMode);
            return;
        }
        Transaction transaction = zooKeeper.transaction();
        //todo sync cache
        List<String> nodes = PathUtil.getPathOrderNodes(rootNode, key);
        for (int i = 0; i < nodes.size(); i++) {
            // todo contrast cache
            if (checkExists(nodes.get(i))){
                System.out.println("exist:" + nodes.get(i));
                continue;
            }
            System.out.println("not exist:" + nodes.get(i));
            if (i == nodes.size() - 1){
                createInTransaction(nodes.get(i), value.getBytes(StringUtil.UTF_8), createMode, transaction);
            } else {
                createInTransaction(nodes.get(i), NOTHING_DATA, createMode, transaction);
            }
        }
        
        // todo org.apache.zookeeper.KeeperException$NodeExistsException: KeeperErrorCode = NodeExists
        transaction.commit();
    }
    
    private Transaction createInTransaction(final String key, byte[] data, final CreateMode createMode, final Transaction transaction){
        return transaction.create(PathUtil.getRealPath(rootNode, key), data, authorities, createMode);
    }
    
    public void update(final String key, final String value) throws KeeperException, InterruptedException {
        zooKeeper.setData(PathUtil.getRealPath(rootNode, key), value.getBytes(StringUtil.UTF_8), VERSION);
    }
    
    public void updateInTransaction(final String key, final String value) throws KeeperException, InterruptedException {
        String realPath = PathUtil.getRealPath(rootNode, key);
        zooKeeper.transaction().check(realPath, VERSION).setData(realPath, value.getBytes(StringUtil.UTF_8), VERSION).commit();
    }
    
    public void deleteOnlyCurrent(final String key) throws KeeperException, InterruptedException {
        zooKeeper.delete(PathUtil.getRealPath(rootNode, key), VERSION);
    }
    
    private void deleteOnlyCurrent(final String key, final Transaction transaction) throws KeeperException, InterruptedException {
        zooKeeper.delete(PathUtil.getRealPath(rootNode, key), VERSION);
    }
    
    public void deleteOnlyCurrent(final String key, final AsyncCallback.VoidCallback callback, final Object ctx) throws KeeperException, InterruptedException {
        zooKeeper.delete(PathUtil.getRealPath(rootNode, key), VERSION, callback, ctx);
    }
    
    public void deleteCurrentBranch(final String key) throws KeeperException, InterruptedException {
        if (key.indexOf(PathUtil.PATH_SEPARATOR) < -1){
            this.deleteOnlyCurrent(key);
            return;
        }
        Transaction transaction = zooKeeper.transaction();
        //todo branch check
        Stack<String> pathStack = PathUtil.getPathReverseNodes(rootNode, key);
        while (!pathStack.empty()){
            String node = pathStack.pop();
            // contrast cache
            if (checkExists(node)){
                transaction.delete(node, VERSION);
                System.out.println("delete : " + node);
            }
        }
        transaction.commit();
    }
    
    /*
    * closed beta
    * 当前实现方法用于缓存方式
    * 缓存实现后此类判断换为异常方式（包括创建）
    * 用事务不能用异常
    */
    public void deleteAllChild(final String key) throws KeeperException, InterruptedException {
        String realPath = PathUtil.getRealPath(rootNode, key);
        try {
            this.deleteOnlyCurrent(realPath);
            System.out.println("delete : " + realPath);
        }catch (KeeperException.NotEmptyException ee){
            List<String> children = this.getChildren(realPath);
            for (String child : children) {
                child = realPath + PathUtil.PATH_SEPARATOR + child;
                this.deleteAllChild(child);
            }
            this.deleteOnlyCurrent(realPath);
            System.out.println("delete : " + realPath);
        } catch (KeeperException.NoNodeException ee){
            System.out.println(ee.getMessage());
            return;
        }
    }
    
    void registerWatch(final Listener listener){
        if (watchRegistered){
            return;
        }
        watchRegistered = true;
        watchers.put(rootNode, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                listener.process(event);
            }
        });
    }

    public Watcher registerWatch(final String key, final Listener listener){
        String path = PathUtil.getRealPath(rootNode, key);
//        listener.setKey(path);
        Watcher watcher = new Watcher() {
            @Override
            public void process(WatchedEvent event) {
                listener.process(event);
            }
        };
        watchers.put(path, watcher);
        return watcher;
    }
    
    public void unregisterWatch(final String key){
        if (StringUtil.isNullOrBlank(key)){
            throw new IllegalArgumentException("key should not be blank");
        }
        String path = PathUtil.getRealPath(rootNode, key);
        if (watchers.containsKey(path)){
            watchers.remove(path);
        }
    }
    
    public void close() throws InterruptedException {
        zooKeeper.close();
    }
}

