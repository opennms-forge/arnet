package org.opennms.arnet.api;

public interface ConsumerService {

    void accept(Consumer consumer);

    void start();

    void stop();

}
