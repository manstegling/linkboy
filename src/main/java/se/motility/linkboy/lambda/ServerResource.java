package se.motility.linkboy.lambda;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.Server;

public class ServerResource implements Resource {

    private final Server server = new Server();
    private static final Logger LOG = LoggerFactory.getLogger(ServerResource.class);

    public ServerResource() {
        Core.getGlobalContext().register(this);
    }

    public Server server() {
        return server;
    }

    /* AWS Lambda SnapStart functionality used to preload data into memory */

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        LOG.info("Before checkpoint called");
        server.initAll();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // do nothing
    }

}