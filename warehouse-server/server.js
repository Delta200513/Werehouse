const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');

const app = express();
const PORT = 3000;

app.use(cors());
app.use(bodyParser.json());

// База данных в памяти
let tasks = [
    { id: 1, title: "Приемка коробок", type: "receiving", progress: 0, total: 10 },
    { id: 2, title: "Сборка заказа #101", type: "picking", progress: 0, total: 5 }
];
let scans = [];

// API для задач
app.get('/api/tasks', (req, res) => res.json(tasks));

app.post('/api/tasks/add', (req, res) => {
    const { title, type, total } = req.body;
    const newTask = { id: Date.now(), title, type, progress: 0, total: parseInt(total) };
    tasks.push(newTask);
    res.json(newTask);
});

app.delete('/api/tasks/:id', (req, res) => {
    tasks = tasks.filter(t => t.id != req.params.id);
    res.json({ success: true });
});

// ПРИЕМ СКАНА С УЧЕТОМ ТИПА
app.post('/api/scan', (req, res) => {
    const { qrCode, workerName, scanType } = req.body;
    console.log(`[SCAN] Код: ${qrCode}, Тип: ${scanType} от ${workerName}`);

    scans.push({ qrCode, worker: workerName, type: scanType, timestamp: new Date() });

    // Ищем ПЕРВУЮ подходящую по ТИПУ задачу, которая еще не готова
    const activeTask = tasks.find(t => t.type === scanType && t.progress < t.total);
    if (activeTask) activeTask.progress++;

    res.json({ success: true, taskUpdated: activeTask ? activeTask.title : "Нет активных задач этого типа" });
});

app.get('/api/scans', (req, res) => res.json(scans));
app.post('/api/reset', (req, res) => { tasks = []; scans = []; res.json({ success: true }); });
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'index.html')));

app.listen(PORT, '0.0.0.0', () => console.log(`✅ Сервер запущен на порту ${PORT}`));