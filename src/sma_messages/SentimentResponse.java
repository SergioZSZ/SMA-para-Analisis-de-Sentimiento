package sma_messages;

import java.io.Serializable;

public class SentimentResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String postId;
    private final String commentId;
    private final String text;
    private final String sentiment;
    private final double score;

    public SentimentResponse(String postId, String commentId, String text, String sentiment, double score) {
        this.postId = postId;
        this.commentId = commentId;
        this.text = text;
        this.sentiment = sentiment;
        this.score = score;
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

    public String getSentiment() {
        return sentiment;
    }

    public double getScore() {
        return score;
    }
}