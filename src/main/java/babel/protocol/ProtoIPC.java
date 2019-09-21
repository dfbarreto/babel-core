package babel.protocol;

public abstract class ProtoIPC {

    public enum Type { REPLY, REQUEST}

    private Type type;

    public ProtoIPC(Type t){
        this.type = t;
    }

    public Type getType() {
        return type;
    }
}
