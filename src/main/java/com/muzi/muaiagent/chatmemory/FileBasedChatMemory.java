package com.muzi.muaiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 基于文件持久化的对话记忆存储。
 * <p>
 * 实现 {@link ChatMemoryRepository} 接口，作为 {@link org.springframework.ai.chat.memory.MessageWindowChatMemory}
 * 的底层存储使用。每条会话的消息以 Kryo 序列化格式保存到本地文件中。
 * </p>
 */
public class FileBasedChatMemory implements ChatMemoryRepository {

    private final String baseDir;
    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        // 设置实例化策略，支持无参构造器缺失的类
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    /**
     * 构造对象时指定文件保存目录。
     *
     * @param dir 消息文件存储的根目录
     */
    public FileBasedChatMemory(String dir) {
        this.baseDir = dir;
        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
    }

    @Override
    public List<String> findConversationIds() {
        File dir = new File(baseDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .map(f -> f.getName().replace(".kryo", ""))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            List<Message> messages = kryo.readObject(input, ArrayList.class);
            return messages != null ? messages : new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, new ArrayList<>(messages));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(baseDir, conversationId + ".kryo");
    }
}
