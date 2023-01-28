package se.motility.linkboy;

import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;

public class ServerResource implements Resource {

    private final Server server = new Server();

    public ServerResource() {
        Core.getGlobalContext().register(this);
    }

    public Server server() {
        return server;
    }

    /* AWS Lambda SnapStart functionality used to preload data into memory */

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        server.initAll();
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        // do nothing
    }

}
