package org.opennms.oia.streaming.client.api;

public interface ConsumerService {

    void accept(Consumer consumer);

    void dismiss(Consumer consumer);

    void start();

    void stop();

}
