interface Config {
    port: number;
    host: string;
}

class Server {
    private config: Config;

    constructor(config: Config) {
        this.config = config;
    }

    start(): void {
        console.log(`Server starting on ${this.config.host}:${this.config.port}`);
    }

    stop(): void {
        console.log("Server stopped");
    }
}

enum Status {
    RUNNING = "running",
    STOPPED = "stopped",
}

function createServer(port: number): Server {
    return new Server({ port, host: "localhost" });
}

const defaultPort = 8080;
