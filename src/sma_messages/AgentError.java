package sma_messages;

import java.io.Serializable;

public class AgentError implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String postId;
    private final String commentId;
    private final String reason;

    public AgentError(String postId, String commentId, String reason) {
        this.postId = postId;
        this.commentId = commentId;
        this.reason = reason;
    }

    public String getPostId() {
        return postId;
    }

    public String getCommentId() {
        return commentId;
    }

    public String getReason() {
        return reason;
    }
}