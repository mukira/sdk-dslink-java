package org.dsa.iot.responder;

import org.dsa.iot.dslink.node.Node;

/**
 * Creates a bunch of nodes to test children updates.
 * @author Samuel Grenier
 */
public class Replicator {

    private final Node node;

    public Replicator(Node parent) {
        node = parent;
    }

    public void start() {
        startThread();
    }

    private void startThread() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 100; i++) {
                        Thread.sleep(1000);
                        node.createChild(String.valueOf(i)).build();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
