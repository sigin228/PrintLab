# Маршруты

- GET `/`, `/catalog`, `/catalog/{id}`, `/designer/{productId}` — главная, каталог и гостевой редактор.
- GET/POST `/register`, GET/POST `/login`, POST `/logout` — учётная запись и сессия.
- GET/POST `/profile`, GET `/my-designs` — кабинет и дизайны.
- POST `/api/designs`, GET/PUT/DELETE `/api/designs/{id}`; POST `/api/designs/uploads`, GET `/api/designs/uploads/{fileId}`, POST `/api/designs/uploads/{fileId}/lease` — дизайны и приватные файлы владельца.
- GET `/cart`, POST `/cart/items`, `/cart/items/{id}/quantity`, `/cart/items/{id}/remove`, `/cart/clear` — корзина.
- GET/POST `/checkout`, POST `/checkout/preview` — оформление и расчёт скидки.
- GET `/orders`, `/orders/{id}`, `/orders/{id}/images/{fileId}`; POST `/orders/{id}/cancel`, `/orders/{id}/pay` — заказы и демо-оплата владельца.
- GET `/wallet`, POST `/wallet/top-up` — виртуальный баланс.
- `/admin`, `/admin/products/**`, `/admin/orders/**`, `/admin/prints`, `/admin/promos` — административные операции, включая управление промокодами и сообщения по заказу.
- GET `/admin-prints/{id}/image` и `/product-images/{id}` — изображения принтов и товаров; содержимое загружается администратором.

Изменяющие запросы требуют CSRF и соответствующей авторизации.
