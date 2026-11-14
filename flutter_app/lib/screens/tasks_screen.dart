import 'package:flutter/material.dart';
import '../models/task_item.dart';

class TasksScreen extends StatefulWidget {
  const TasksScreen({Key? key}) : super(key: key);

  @override
  State<TasksScreen> createState() => _TasksScreenState();
}

class _TasksScreenState extends State<TasksScreen> {
  final List<TaskItem> _taskList = [
    TaskItem(id: '1', title: 'حل أسئلة امتحان الفيزياء الحديثة', subject: 'فيزياء', date: 'اليوم', isCompleted: true),
    TaskItem(id: '2', title: 'مراجعة كلمات وقواعد Unit 7 و 8', subject: 'لغة إنجليزية', date: 'اليوم', isCompleted: true),
    TaskItem(id: '3', title: 'استذكار وتلخيص الكيمياء العضوية', subject: 'كيمياء', date: 'غداً', isCompleted: false),
    TaskItem(id: '4', title: 'قراءة سورة الكهف وسورة المنافقون', subject: 'إسلاميات', date: 'الجمعة', isCompleted: false),
    TaskItem(id: '5', title: 'حل نموذج تفاضل وتكامل شامل', subject: 'رياضيات', date: 'السبت', isCompleted: false),
  ];

  final _textController = TextEditingController();
  String _selectedSubject = 'عام';

  void _addTask() {
    if (_textController.text.trim().isNotEmpty) {
      setState(() {
        _taskList.add(TaskItem(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          title: _textController.text.trim(),
          subject: _selectedSubject,
          date: 'اليوم',
          isCompleted: false,
        ));
        _textController.clear();
      });
      Navigator.pop(context);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('المهام والملاحظات الدراسية 📝', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF00897B),
        foregroundColor: Colors.white,
      ),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: const Color(0xFF00897B),
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add),
        label: const Text('إضافة مهمة'),
        onPressed: () {
          showModalBottomSheet(
            context: context,
            isScrollControlled: true,
            builder: (ctx) => Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 20,
                bottom: MediaQuery.of(ctx).viewInsets.bottom + 20,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('إضافة مهمة دراسية جديدة', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _textController,
                    decoration: InputDecoration(
                      hintText: 'عنوان المهمة أو الواجب المطلوب...',
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: _addTask,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00897B),
                      foregroundColor: Colors.white,
                      minimumSize: const Size.fromHeight(46),
                      shape: RoundedCornerShape(12),
                    ),
                    child: const Text('حفظ المهمة'),
                  ),
                ],
              ),
            ),
          );
        },
      ),
      body: ListView.builder(
        padding: const EdgeInsets.all(16),
        itemCount: _taskList.length,
        itemBuilder: (context, index) {
          final item = _taskList[index];
          return Card(
            margin: const EdgeInsets.only(bottom: 10),
            shape: RoundedCornerShape(12),
            child: CheckboxListTile(
              value: item.isCompleted,
              title: Text(
                item.title,
                style: TextStyle(
                  decoration: item.isCompleted ? TextDecoration.lineThrough : null,
                  fontWeight: FontWeight.bold,
                ),
              ),
              subtitle: Text('${item.subject} • ${item.date}'),
              onChanged: (val) {
                setState(() {
                  item.isCompleted = val ?? false;
                });
              },
            ),
          );
        },
      ),
    );
  }
}
