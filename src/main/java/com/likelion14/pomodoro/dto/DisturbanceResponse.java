import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DisturbanceResponse {
    private Long id;
    private String type;    // "findSemicolon", "stroop" 등
    private String content; // "다음 중 ';'은 몇 개인가요? \n asdf;lkj..."
    private String attackerNickname;
}