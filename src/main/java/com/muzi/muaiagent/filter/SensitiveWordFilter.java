package com.muzi.muaiagent.filter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * ============================================================================
 * 基于 DFA（Deterministic Finite Automaton，确定有限自动机）的敏感词过滤器
 * ============================================================================
 *
 * 【核心算法原理 —— DFA 状态机 / Trie 前缀树】
 *
 * DFA 的核心思想是把所有敏感词构建成一棵"前缀树"（Trie），
 * 树的每条边代表一个字符，从根到某个"结束节点"的路径就是一个完整的敏感词。
 *
 * 举个例子，假设词库中有 "暴力" 和 "暴乱" 两个词，构建出来的 Trie 如下：
 *
 *        root（根节点）
 *         │
 *        [暴]           ← 第一层：两个词都以"暴"开头，共享同一个节点
 *        /  \
 *      [力]  [乱]       ← 第二层：分别对应 "暴力" 和 "暴乱"
 *       ↓     ↓
 *      END   END        ← 结束标记，表示从根到此节点是一个完整的敏感词
 *
 * 【为什么用 DFA 而不是简单的字符串 contains？】
 *
 * 假设词库有 10000 个词，用户输入一段 200 字的文本：
 *   - 暴力做法：对每个词调用 text.contains(word)，最坏情况 10000 × 200 = 200 万次比较
 *   - DFA 做法：对文本的每个起始位置，沿 Trie 走一遍，时间复杂度 O(文本长度 × 最长词长度)
 *     即 200 × 10 = 2000 次操作，性能差距巨大
 *
 * 【工作流程】
 *   1. 应用启动 → @PostConstruct → 加载词库 → 构建 DFA 状态机
 *   2. 用户输入 → containsSensitiveWord() 检测是否有敏感词
 *   3. 若命中 → replaceSensitiveWords() 将敏感词替换为 "***"
 *
 * @see SensitiveWordAdvisor 在 Spring AI 的 Advisor 链中调用本过滤器
 */
@Component  // 注册为 Spring Bean，可被 @Autowired / 构造器注入到其他组件中
public class SensitiveWordFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordFilter.class);

    /**
     * 敏感词文件的 classpath 路径。
     * 文件放在 src/main/resources/ 下，Spring 的 ClassPathResource 可以直接读取。
     * 好处：词库和代码解耦，修改词库不需要改代码、不需要重新编译。
     */
    private static final String WORD_FILE_PATH = "sensitive-words.txt";

    /**
     * 敏感词的替换掩码。
     * 当检测到敏感词时，用这个字符串替换原文。例如 "暴力内容" → "***内容"
     */
    private static final String REPLACEMENT = "***";

    /**
     * DFA 结束标记（End-of-Word Marker）。
     *
     * 【设计说明】
     * 在 Trie 中，我们需要一种方式标记"从根到当前节点构成一个完整的敏感词"。
     * 这里用一个特殊的 key '\0'（空字符，不会出现在正常文本中）来标记结束。
     *
     * 比如 "暴力" 这个词，在 Trie 中的路径是：root → '暴' → '力'
     * 我们会在 '力' 这个节点中加入一个 key='\0' 的条目，表示"到这里是一个完整词"。
     *
     * 为什么需要结束标记？
     * 考虑词库中有 "中国" 和 "中国人" 两个词，当扫描到 "中国人" 时：
     *   - 走到 '国' 节点 → 发现结束标记 → 确认匹配了 "中国"（长度 2）
     *   - 继续走到 '人' 节点 → 发现结束标记 → 确认匹配了 "中国人"（长度 3）
     *   - 取最长匹配 "中国人"，这就是"最长匹配"策略
     */
    @SuppressWarnings("unchecked")
    private static final Map<String, Object> END_FLAG = Collections.singletonMap("isEnd", true);

    /**
     * DFA 状态机的根节点（Trie 的根）。
     *
     * 【数据结构说明】
     * 这里用 Map<Character, Object> 来表示 Trie 节点：
     *   - key = 字符（Character）：代表从当前节点出发的下一个字符
     *   - value = 子节点（也是 Map<Character, Object>）或结束标记（END_FLAG）
     *
     * 用 Object 作为 value 的类型是为了同时容纳"子节点 Map"和"结束标记 Map"两种值。
     * 虽然类型安全性稍弱，但避免了定义额外的 Node 类，代码更简洁。
     *
     * 示例：词库 {"暴力", "赌博"} 构建后的 root 结构：
     * root = {
     *   '暴' → { '力' → { '\0' → END_FLAG } },
     *   '赌' → { '博' → { '\0' → END_FLAG } }
     * }
     */
    private final Map<Character, Object> root = new HashMap<>();

    /**
     * 已加载的敏感词集合。
     * 主要用于调试和管理：可以通过 getWordSet() 查看当前加载了哪些词。
     * 使用 Set 保证去重（即使词库文件中同一个词出现多次也只存一份）。
     */
    private final Set<String> wordSet = new HashSet<>();

    /**
     * Spring Bean 初始化钩子。
     *
     * 【@PostConstruct 的作用】
     * 在 Spring 完成依赖注入之后、Bean 正式可用之前，自动调用此方法。
     * 适合做一次性初始化工作（如加载文件、构建数据结构）。
     *
     * 执行时机：Spring 容器启动 → 创建 SensitiveWordFilter Bean → 调用 init()
     *
     * 注意：@PostConstruct 来自 jakarta.annotation 包（Spring Boot 3.x 使用 Jakarta EE 规范）。
     * 在 Spring Boot 2.x 中对应的是 javax.annotation.PostConstruct。
     */
    @PostConstruct
    public void init() {
        loadWords();
        logger.info("敏感词过滤器初始化完成，共加载 {} 个敏感词", wordSet.size());
    }

    /**
     * 从 classpath 下的敏感词文件加载词库，并逐词构建 DFA 状态机。
     *
     * 【Spring ClassPathResource】
     * ClassPathResource 是 Spring 提供的资源加载抽象，可以从 classpath 中读取文件。
     * 相比直接用 Thread.currentThread().getContextClassLoader().getResourceAsStream()，
     * 它更简洁，并且与 Spring 的资源抽象体系统一。
     *
     * 【文件格式约定】
     *   - 每行一个敏感词
     *   - # 开头的行为注释，会被忽略
     *   - 空行会被忽略
     *   - 词的前后空格会被 trim 掉
     */
    @SuppressWarnings("unchecked")
    private void loadWords() {
        // ClassPathResource 会从 classpath 根路径查找文件
        // 对应 Maven 项目就是 src/main/resources/sensitive-words.txt
        ClassPathResource resource = new ClassPathResource(WORD_FILE_PATH);

        // try-with-resources：自动关闭流，无需手动 finally
        // StandardCharsets.UTF_8：确保中文不会乱码
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim();

                // 跳过空行和注释行（以 # 开头）
                if (word.isEmpty() || word.startsWith("#")) {
                    continue;
                }

                // 记录到词库集合（去重）
                wordSet.add(word);

                // 将这个词加入 DFA 状态机
                addWordToDfa(word);
            }
        } catch (IOException e) {
            // 加载失败时只打印警告，不抛异常阻断启动
            // 这样即使词库文件缺失，应用也能正常运行（只是没有敏感词过滤）
            logger.warn("加载敏感词文件失败: {}", WORD_FILE_PATH, e);
        }
    }

    /**
     * 将一个敏感词插入 DFA 状态机（Trie 前缀树）。
     *
     * 【算法详解 —— 以 "暴力" 为例】
     *
     * 初始状态：root = {}
     *
     * 第 1 步，处理字符 '暴'（i=0）：
     *   - root 中没有 '暴' → 创建新节点 new HashMap<>()
     *   - root.put('暴', newNode)
     *   - currentNode 指向 newNode
     *   - 此时 root = { '暴' → {} }
     *
     * 第 2 步，处理字符 '力'（i=1，也是最后一个字符）：
     *   - currentNode（暴的子节点）中没有 '力' → 创建新节点
     *   - currentNode.put('力', newerNode)
     *   - currentNode 指向 newerNode
     *   - 因为 i == word.length()-1，标记结束：currentNode.put('\0', END_FLAG)
     *
     * 最终：root = { '暴' → { '力' → { '\0' → END_FLAG } } }
     *
     * 【前缀共享】
     * 如果之后再加入 "暴乱"：
     *   - 处理 '暴' 时，root 中已有 '暴' → 复用已有节点（不创建新的）
     *   - 处理 '乱' 时，在 '暴' 的子节点下新增 '乱'
     * 这样 "暴力" 和 "暴乱" 共享了 '暴' 这个前缀节点，节省内存。
     *
     * @param word 要插入的敏感词
     */
    @SuppressWarnings("unchecked")
    private void addWordToDfa(String word) {
        // currentNode 从根节点开始，逐字符向下遍历
        Map<Character, Object> currentNode = root;

        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);  // 取出当前字符

            // 查找当前字符对应的子节点
            Object child = currentNode.get(ch);

            if (child == null) {
                // 子节点不存在 → 创建新节点并挂到当前节点下
                Map<Character, Object> newNode = new HashMap<>();
                currentNode.put(ch, newNode);
                currentNode = newNode;  // 移动到新节点
            } else {
                // 子节点已存在 → 直接复用（前缀共享的关键）
                currentNode = (Map<Character, Object>) child;
            }

            // 如果是词的最后一个字符，标记结束
            if (i == word.length() - 1) {
                currentNode.put('\0', END_FLAG);
            }
        }
    }

    /**
     * 检查文本中是否包含任意敏感词。
     *
     * 【用法场景】
     * 先调用此方法快速判断，如果返回 true 再调用 replaceSensitiveWords() 做替换。
     * 这样避免了不必要的替换操作（没有敏感词时直接返回原文本更快）。
     *
     * 【算法流程】
     * 从文本的每个位置开始尝试匹配（外层循环），
     * 一旦在某个位置找到了匹配（matchLength > 0），立即返回 true。
     *
     * @param text 待检查的用户输入文本
     * @return true = 文本中包含至少一个敏感词
     */
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 从每个位置开始尝试匹配
        for (int i = 0; i < text.length(); i++) {
            if (matchLength(text, i) > 0) {
                return true;  // 在位置 i 处发现了敏感词
            }
        }
        return false;
    }

    /**
     * 将文本中所有敏感词替换为 "***"。
     *
     * 【替换策略 —— 最长匹配优先】
     * 假设词库中有 "中国" 和 "中国人"，输入 "我是中国人"：
     *   - 从位置 2 开始匹配：'中' → '国'（匹配长度 2）→ '人'（匹配长度 3）
     *   - 最长匹配是 "中国人"（长度 3），所以替换 "***" 而不是 "***人"
     *
     * 【算法流程（双指针思想）】
     *   i 指向当前扫描位置：
     *   - 如果位置 i 匹配到敏感词（长度 len）→ 追加 "***"，i 跳过 len 个字符
     *   - 如果位置 i 没有匹配 → 原样追加 text[i]，i 前进 1 步
     *
     * 示例：输入 "涉及暴力和赌博的内容"
     *   - i=0 "涉" → 不匹配 → 追加 "涉"，i=1
     *   - i=1 "及" → 不匹配 → 追加 "及"，i=2
     *   - i=2 "暴力" → 匹配长度 2 → 追加 "***"，i=4
     *   - i=4 "和" → 不匹配 → 追加 "和"，i=5
     *   - i=5 "赌博" → 匹配长度 2 → 追加 "***"，i=7
     *   - i=7 "的内容" → 逐字符不匹配 → 原样追加
     *   结果："涉及***和***的内容"
     *
     * @param text 原始用户输入文本
     * @return 替换敏感词后的文本；若无敏感词则返回原文本
     */
    public String replaceSensitiveWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int len = matchLength(text, i);  // 从位置 i 开始尝试匹配
            if (len > 0) {
                // 匹配到敏感词，用 "***" 替换，并跳过整个敏感词
                result.append(REPLACEMENT);
                i += len;
            } else {
                // 未匹配，保留原字符
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 从文本的指定位置开始，沿 DFA 状态机匹配，返回最长匹配的敏感词长度。
     *
     * 这是整个过滤器的核心方法 —— 一次"探测"操作。
     *
     * 【算法详解 —— 以文本 "中国人很好" 从 startIndex=0 开始匹配为例】
     * 假设词库中有 "中国" 和 "中国人"：
     *
     *   Trie 结构：root → '中' → '国'[END] → '人'[END] → ...
     *
     *   步骤 1: i=0, ch='中', 在 root 中找到 → currentNode 移到 '中' 节点, matchLen=1
     *           '中' 节点没有 '\0' → lastConfirmedLen 不变（还是 0）
     *
     *   步骤 2: i=1, ch='国', 在 '中' 的子节点中找到 → 移到 '国' 节点, matchLen=2
     *           '国' 节点有 '\0' → lastConfirmedLen=2（确认匹配了 "中国"）
     *
     *   步骤 3: i=2, ch='人', 在 '国' 的子节点中找到 → 移到 '人' 节点, matchLen=3
     *           '人' 节点有 '\0' → lastConfirmedLen=3（确认匹配了 "中国人"）
     *
     *   步骤 4: i=3, ch='很', 在 '人' 的子节点中找不到 → break
     *
     *   返回 lastConfirmedLen=3（最长匹配 "中国人"）
     *
     * 【最长匹配 vs 最短匹配】
     * 用 lastConfirmedLen 而非 matchLen 记录结果，确保返回的是"最后一个确认的完整词"的长度。
     * 如果不用最长匹配，遇到 "中国人" 会只替换 "中国" 而留下 "人"，过滤不彻底。
     *
     * @param text       待扫描的文本
     * @param startIndex 开始匹配的位置
     * @return 最长匹配的敏感词长度；0 表示该位置没有命中任何敏感词
     */
    @SuppressWarnings("unchecked")
    private int matchLength(String text, int startIndex) {
        int matchLen = 0;           // 当前沿 Trie 走过的步数
        int lastConfirmedLen = 0;   // 最后一个确认的完整敏感词长度
        Map<Character, Object> currentNode = root;  // 从根节点开始

        for (int i = startIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            Object child = currentNode.get(ch);

            if (child == null) {
                // 当前字符在 Trie 中找不到 → 匹配中断
                break;
            }

            // 沿 Trie 向下走一步
            currentNode = (Map<Character, Object>) child;
            matchLen++;

            // 检查当前节点是否是某个敏感词的结尾
            if (currentNode.containsKey('\0')) {
                // 记录这个确认的匹配长度（可能被更长的匹配覆盖）
                lastConfirmedLen = matchLen;
            }
        }

        return lastConfirmedLen;
    }

    /**
     * 获取已加载的敏感词集合（只读视图）。
     *
     * Collections.unmodifiableSet() 返回一个不可修改的包装集合，
     * 外部只能查看，不能添加/删除词，保证内部数据安全。
     * 可用于管理接口查看当前加载了哪些敏感词。
     */
    public Set<String> getWordSet() {
        return Collections.unmodifiableSet(wordSet);
    }
}
