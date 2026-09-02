package project.drivers;

public enum Browser {
    CHROME
            {
                public AbstractDriver getDriverFactory(){
                    return new ChromeFactory();
                }
            },
    EDGE
            {
                public AbstractDriver getDriverFactory(){
                    return new EdgeFactory();
                }
            };

    public abstract AbstractDriver getDriverFactory();
}
