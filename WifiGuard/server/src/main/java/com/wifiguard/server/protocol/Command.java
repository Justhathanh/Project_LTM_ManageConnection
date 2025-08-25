package com.wifiguard.server.protocol;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enum nâng cao định nghĩa các lệnh mà server có thể xử lý
 * Bao gồm validation, thông tin trợ giúp và các phương thức tiện ích
 */
public enum Command {
    /**
     * Liệt kê tất cả thiết bị đã phát hiện
     */
    LIST("Liệt kê tất cả thiết bị", "LIST", false, 0, 0),
    
    /**
     * Liệt kê tất cả thiết bị trong allowlist
     */
    ALLOWLIST("Liệt kê tất cả thiết bị trong allowlist", "ALLOWLIST", false, 0, 0),
    
    /**
     * Thêm thiết bị vào danh sách cho phép
     */
    ADD("Thêm thiết bị vào danh sách cho phép", "ADD <MAC> [HOSTNAME] [IP]", true, 1, 3),
    
    /**
     * Xóa thiết bị khỏi danh sách cho phép
     */
    DEL("Xóa thiết bị khỏi danh sách cho phép", "DEL <MAC>", true, 1, 1),
    
    /**
     * Lấy trạng thái server
     */
    STATUS("Lấy trạng thái server", "STATUS", false, 0, 0),
    
    /**
     * Thoát kết nối
     */
    QUIT("Thoát kết nối", "QUIT", false, 0, 0);
    
    // Metadata của lệnh
    private final String description;
    private final String usage;
    private final boolean requiresArguments;
    private final int minArgs;
    private final int maxArgs;
    
    // Constructor
    Command(String description, String usage, boolean requiresArguments, int minArgs, int maxArgs) {
        this.description = description;
        this.usage = usage;
        this.requiresArguments = requiresArguments;
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
    }
    
    // Các getter
    public String getDescription() { return description; }
    public String getUsage() { return usage; }
    public boolean requiresArguments() { return requiresArguments; }
    public int getMinArgs() { return minArgs; }
    public int getMaxArgs() { return maxArgs; }
    
    /**
     * Kiểm tra xem lệnh có cần tham số không
     */
    public boolean needsArguments() {
        return requiresArguments;
    }
    
    /**
     * Kiểm tra số lượng tham số cho lệnh này
     */
    public boolean isValidArgCount(int argCount) {
        return argCount >= minArgs && argCount <= maxArgs;
    }
    
    /**
     * Lấy thông báo lỗi validation cho số lượng tham số
     */
    public String getArgValidationMessage(int argCount) {
        if (argCount < minArgs) {
            return String.format("Lệnh %s cần ít nhất %d tham số. Cách sử dụng: %s", 
                               name(), minArgs, usage);
        }
        if (argCount > maxArgs) {
            return String.format("Lệnh %s chấp nhận tối đa %d tham số. Cách sử dụng: %s", 
                               name(), maxArgs, usage);
        }
        return null; // Hợp lệ
    }
    
    /**
     * Kiểm tra xem lệnh có phải là lệnh hệ thống không (không phải hành động người dùng)
     */
    public boolean isSystemCommand() {
        return this == STATUS || this == QUIT;
    }
    
    /**
     * Kiểm tra xem lệnh có sửa đổi dữ liệu không
     */
    public boolean isModifyingCommand() {
        return this == ADD || this == DEL;
    }
    
    /**
     * Kiểm tra xem lệnh có chỉ đọc không
     */
    public boolean isReadOnly() {
        return this == LIST || this == STATUS;
    }
    
    /**
     * Lấy danh mục lệnh
     */
    public String getCategory() {
        if (isSystemCommand()) return "Hệ thống";
        if (isModifyingCommand()) return "Sửa đổi";
        if (isReadOnly()) return "Truy vấn";
        return "Khác";
    }
    
    /**
     * Lấy tất cả lệnh dưới dạng map để dễ tra cứu
     */
    public static Map<String, Command> getCommandMap() {
        return Arrays.stream(values())
                .collect(Collectors.toMap(Command::name, cmd -> cmd));
    }
    
    /**
     * Lấy lệnh theo danh mục
     */
    public static Map<String, java.util.List<Command>> getCommandsByCategory() {
        return Arrays.stream(values())
                .collect(Collectors.groupingBy(Command::getCategory));
    }
    
    /**
     * Lấy tất cả tên lệnh dưới dạng chuỗi phân cách bằng dấu phẩy
     */
    public static String getAllCommandNames() {
        return Arrays.stream(values())
                .map(Command::name)
                .collect(Collectors.joining(", "));
    }
    
    /**
     * Lấy thông tin trợ giúp cho tất cả lệnh
     */
    public static String getHelpText() {
        StringBuilder help = new StringBuilder();
        help.append("Các lệnh có sẵn:\n");
        
        Map<String, java.util.List<Command>> byCategory = getCommandsByCategory();
        byCategory.forEach((category, commands) -> {
            help.append("\n").append(category).append(":\n");
            commands.forEach(cmd -> {
                help.append("  ").append(cmd.name()).append(" - ").append(cmd.description).append("\n");
                help.append("    Cách sử dụng: ").append(cmd.usage).append("\n");
            });
        });
        
        return help.toString();
    }
    
    /**
     * Lấy trợ giúp cho lệnh cụ thể
     */
    public String getHelp() {
        StringBuilder help = new StringBuilder();
        help.append("Lệnh: ").append(name()).append("\n");
        help.append("Mô tả: ").append(description).append("\n");
        help.append("Cách sử dụng: ").append(usage).append("\n");
        help.append("Danh mục: ").append(getCategory()).append("\n");
        help.append("Tham số: ").append(minArgs).append("-").append(maxArgs).append("\n");
        help.append("Cần tham số: ").append(requiresArguments ? "Có" : "Không");
        
        return help.toString();
    }
    
    /**
     * Phân tích lệnh từ chuỗi (không phân biệt hoa thường)
     */
    public static Command parse(String commandStr) {
        if (commandStr == null || commandStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Chuỗi lệnh không thể null hoặc rỗng");
        }
        
        try {
            return valueOf(commandStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Lệnh không xác định: " + commandStr + 
                                           ". Các lệnh có sẵn: " + getAllCommandNames());
        }
    }
    
    /**
     * Kiểm tra xem chuỗi có phải là lệnh hợp lệ không
     */
    public static boolean isValidCommand(String commandStr) {
        if (commandStr == null || commandStr.trim().isEmpty()) {
            return false;
        }
        
        try {
            valueOf(commandStr.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    @Override
    public String toString() {
        return name();
    }
}
