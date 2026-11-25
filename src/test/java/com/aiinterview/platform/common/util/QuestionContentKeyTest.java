package com.aiinterview.platform.common.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khoá chống trùng ngữ nghĩa của câu hỏi: hai câu cùng ý nhưng khác cách diễn đạt phải ra
 * cùng khoá, còn hai câu trái nghĩa phải khác khoá. Đây là hàng rào thứ hai sau
 * {@code fingerprint} nên sai ở đây hoặc là lọt câu trùng, hoặc là chặn nhầm câu đúng.
 *
 * <p>Mỗi test chia 3 khối comment: <b>Arrange</b> → <b>Act</b> → <b>Assert</b>.</p>
 */
class QuestionContentKeyTest {

    @Test
    void keepsSameKeyWhenLeaderVerbAndTokenOrderChange() {
        // Arrange: cùng một câu hỏi, một bên là câu hỏi trực tiếp, một bên là mệnh lệnh
        String asQuestion = "What is Docker networking?";
        String asImperative = "Docker networking — explain.";

        // Act: chuẩn hoá cả hai về khoá nội dung
        String first = QuestionContentKey.of(asQuestion);
        String second = QuestionContentKey.of(asImperative);

        // Assert: "what is" và "explain" đều quy về "describe" nên phải trùng khoá
        assertThat(first).isEqualTo(second).hasSize(64);
    }

    @Test
    void keepsSameKeyForAdvantageSynonyms() {
        // Arrange: "advantages" và "benefits" là hai cách gọi cùng một nhóm ý
        String withAdvantages = "List the advantages of Docker volumes.";
        String withBenefits = "What are the benefits of Docker volumes?";

        // Act: chuẩn hoá, trong đó số nhiều phải quy về dạng số ít trước khi tra synonym
        String first = QuestionContentKey.of(withAdvantages);
        String second = QuestionContentKey.of(withBenefits);

        // Assert: cả hai quy về "pro" nên trùng khoá
        assertThat(first).isEqualTo(second);
    }

    @Test
    void keepsSameKeyForDrawbackAndWeakness() {
        // Arrange: cùng nhóm "nhược điểm" nhưng khác hậu tố số nhiều
        String withDrawbacks = "What are the drawbacks of this design?";
        String withWeakness = "What is the weakness of this design?";

        // Act
        String first = QuestionContentKey.of(withDrawbacks);
        String second = QuestionContentKey.of(withWeakness);

        // Assert
        assertThat(first).isEqualTo(second);
    }

    @Test
    void keepsSameKeyForTradeOffSpellings() {
        // Arrange: "trade-off" và "tradeoff" chỉ khác dấu gạch nối
        String hyphenated = "Explain the trade-off of caching at the edge.";
        String joined = "Explain the tradeoff of caching at the edge.";

        // Act
        String first = QuestionContentKey.of(hyphenated);
        String second = QuestionContentKey.of(joined);

        // Assert
        assertThat(first).isEqualTo(second);
    }

    @Test
    void keepsNegatedQuestionApart() {
        // Arrange: hai câu chỉ khác nhau một từ phủ định, nghĩa trái ngược hoàn toàn
        String positive = "When should you use a cache?";
        String negated = "When should you not use a cache?";

        // Act
        String first = QuestionContentKey.of(positive);
        String second = QuestionContentKey.of(negated);

        // Assert: "not" là từ bảo vệ nên không được bỏ, hai khoá phải khác nhau
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void keepsComparisonDirectionApart() {
        // Arrange: "more" và "less" là hai hướng so sánh ngược nhau
        String more = "Use more replicas for the read path.";
        String less = "Use less replicas for the read path.";

        // Act
        String first = QuestionContentKey.of(more);
        String second = QuestionContentKey.of(less);

        // Assert
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void ignoresCasePunctuationAndRepeatedWhitespace() {
        // Arrange: cùng một câu nhưng khác hoa thường, dấu câu và khoảng trắng
        String noisy = "  What   is a LAYER???  ";
        String clean = "what is a layer";

        // Act
        String first = QuestionContentKey.of(noisy);
        String second = QuestionContentKey.of(clean);

        // Assert
        assertThat(first).isEqualTo(second);
    }

    @Test
    void returnsNullWhenTextIsBlank() {
        // Arrange: câu rỗng không sinh được khoá
        String empty = "";
        String spaces = "    ";

        // Act & Assert: null nghĩa là cột content_key để NULL, không tham gia chống trùng
        assertThat(QuestionContentKey.of(null)).isNull();
        assertThat(QuestionContentKey.of(empty)).isNull();
        assertThat(QuestionContentKey.of(spaces)).isNull();
    }

    @Test
    void keepsEveryProtectedWordInTheKey() {
        // Arrange: câu nền không chứa từ bảo vệ nào, nên khoá chỉ đổi khi từ được thêm vào còn sống
        String baseline = "caching at the edge";
        String baselineKey = QuestionContentKey.of(baseline);

        // Act & Assert: thêm một từ bảo vệ phải làm khoá đổi. Nếu từ đó bị coi là stopword
        // thì nó bị bỏ và khoá giữ nguyên — đúng lúc câu trái nghĩa bị gộp làm một
        for (String word : QuestionContentKey.protectedWords()) {
            assertThat(QuestionContentKey.of(word + " " + baseline))
                    .as("từ bảo vệ bị bỏ khỏi khoá: %s", word)
                    .isNotEqualTo(baselineKey);
        }
    }

    @Test
    void neverListsProtectedWordAsStopword() {
        // Arrange: hai danh sách được khai báo độc lập nên hoàn toàn có thể chồng lấn do sơ suất
        Set<String> stopwords = QuestionContentKey.stopwords();
        Set<String> protectedWords = QuestionContentKey.protectedWords();

        // Act: tìm phần giao giữa hai tập
        Set<String> overlap = protectedWords.stream()
                .filter(stopwords::contains)
                .collect(java.util.stream.Collectors.toSet());

        // Assert: từ bảo vệ lọt vào stopword sẽ gộp câu trái nghĩa thành một khoá
        assertThat(overlap).isEmpty();
    }
}
