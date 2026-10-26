package com.aiinterview.platform.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** PUT /api/user/avatar — ảnh gửi lên nguyên dạng data URL, BE không giữ file. */
@Data
public class AvatarUpdateRequest {

    /**
     * Chặn trước cả lúc decode: ký tự đầu chuỗi phải là "data:image/{png|jpeg|webp};base64,".
     * GIF không nhận vì trình duyệt chạy ảnh động trong <img> mà không có cỡ phù hợp cho avatar.
     */
    @NotBlank(message = "Chọn ảnh đại diện trước đã")
    @Pattern(
            regexp = "^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$",
            message = "Ảnh đại diện phải là PNG, JPG hoặc WEBP dạng base64"
    )
    // 2 MB ảnh sau khi base64 hóa dài ~2.8 triệu ký tự; chặn ở đây để không decode chuỗi khổng lồ.
    @Size(max = 2_800_000, message = "Ảnh đại diện tối đa 2 MB")
    private String dataUrl;
}
