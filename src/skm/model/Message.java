package skm.model;

/** A message travelling through the network. Times are virtual seconds. */
public final class Message {
    public final int id;
    public final MsgType type;
    public final int from, to, sn;       // sn = request number (REQUEST only)
    public final double sendTime, arriveTime;
    public final Token token;            // payload (TOKEN only)

    public Message(int id, MsgType type, int from, int to, int sn, double sendTime, double arriveTime, Token token) {
        this.id = id; this.type = type; this.from = from; this.to = to; this.sn = sn;
        this.sendTime = sendTime; this.arriveTime = arriveTime; this.token = token;
    }

    public Message copy() {
        return new Message(id, type, from, to, sn, sendTime, arriveTime, token == null ? null : token.copy());
    }
}
