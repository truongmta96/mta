import {defineConfig} from 'vite';

// Cấu hình Vite cho môi trường phát triển (npm run dev).
export default defineConfig({
    server: {
        proxy: {
            // Chuyển request /api sang Spring Boot, giữ nguyên đường dẫn.
            // Ưu tiên API_URL của tiến trình Vite, mặc định backend trên máy host cổng 8080.
            // Đây là biến cho dev server, không được tự chèn vào bundle frontend.
            // Khi chạy Docker production, Nginx đảm nhiệm proxy thay cho Vite.
            '/api': process.env.API_URL || 'http://localhost:8080'
        }
    }
});
