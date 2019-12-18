package org.opennms.arnet.api;

import org.opennms.arnet.app.domain.NetworkManager;

public interface ConsumerService {

    void accept(Consumer consumer);

    void dismiss(Consumer consumer);

    void start();

    void stop();

}
