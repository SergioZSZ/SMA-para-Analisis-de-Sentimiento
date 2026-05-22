package sma_messages;

import java.io.Serializable;

public class SentimentRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String postId;
    private final String commentId;
    private final String text;

    public SentimentRequest(String postId, String commentId, String text) {
        this.postId = postId;
        this.commentId = commentId;
        this.text = text;
    }

    public String getPostId() {
        return postId;
    }

    public String getCommentId() {
        return commentId;
    }

    public String getText() {
        return text;
    }
}