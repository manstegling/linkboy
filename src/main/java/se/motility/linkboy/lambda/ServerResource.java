package se.motility.linkboy.lambda;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.motility.linkboy.Server;

public class ServerResource implements Resource {

    private static final Logger LOG = LoggerFactory.getLogger(ServerResource.class);
    private final Server server = new Server();

    private final boolean initSearch;
    private final boolean initPathFinder;

    public ServerResource(boolean initSearch, boolean initPathFinder) {
        this.initSearch = initSearch;
        this.initPathFinder = initPathFinder;
        Core.getGlobalContext().register(this);
    }

    public Server server() {
        return server;
    }

    /* AWS Lambda SnapStart functionality used to preload data into memory */

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        LOG.info("Before checkpoint called. Init Search: {}, Init PathFinder: {}",
                initSearch ? "Y" : "N", initPathFinder ? "Y" : "N");
        if (initSearch) {
            server.initSearch();
        }
        if (initPathFinder) {
            server.initPathFinder();
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // do nothing
    }

}
