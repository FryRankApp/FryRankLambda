package com.fryrank.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NonNull;

import java.util.List;

@Data
public class GetAllReviewsOutput {

	@NonNull
	private final List<Review> reviews;

	@SerializedName("next_cursor")
	private final String nextCursor;

	public GetAllReviewsOutput(@NonNull final List<Review> reviews) {
		this.reviews = reviews;
		this.nextCursor = null;
	}

	public GetAllReviewsOutput(@NonNull final List<Review> reviews, final String nextCursor) {
		this.reviews = reviews;
		this.nextCursor = nextCursor;
	}
}
