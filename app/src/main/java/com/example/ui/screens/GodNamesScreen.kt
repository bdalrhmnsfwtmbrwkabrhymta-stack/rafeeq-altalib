package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.MainViewModel
import java.util.Locale

data class GodNameItem(
    val number: Int,
    val name: String,
    val meaning: String,
    val explanation: String,
    val quranRef: String = ""
)

val ALL_99_GOD_NAMES = listOf(
    GodNameItem(1, "اللهُ", "الاسم الأعظم الجامع لصفات الألوهية والربوبية", "المألوه المعبود بحق، الذي لا يستحق العبادة سواه.", "هُوَ اللَّهُ الَّذِي لَا إِلَهَ إِلَّا هُوَ"),
    GodNameItem(2, "الرَّحْمَنُ", "ذو الرحمة الواسعة الشاملة لجميع الخلائق في الدنيا", "الذي وسعت رحمته كل شيء وعمّ بها المؤمن والكافر والبهائم.", "الرَّحْمَٰنُ عَلَى الْعَرْشِ اسْتَوَىٰ"),
    GodNameItem(3, "الرَّحِيمُ", "ذو الرحمة الخاصة بالمؤمنين في الآخرة", "المنعم بلطائفه وتوفيقه على عباده الصالحين.", "وَكَانَ بِالْمُؤْمِنِينَ رَحِيمًا"),
    GodNameItem(4, "المَلِكُ", "المتصرف في ملكه كيف يشاء دون شريك", "الذي له الملك الكامل، المستغني عن كل شيء والمفتقر إليه كل شيء.", "فَتَعَالَى اللَّهُ الْمَلِكُ الْحَقُّ"),
    GodNameItem(5, "القُدُّوسُ", "المنزّه والمطهّر عن كل عيب ونقص", "المنزه عن صفات المخلوقين والعيوب والآفات كلياً.", "الْمَلِكُ الْقُدُّوسُ السَّلَامُ"),
    GodNameItem(6, "السَّلَامُ", "الذي سلم من كل عيب ونقص وسلّم خلقه من ظلمه", "ناشر السلام والأمان بين عباده، ودار السلام هي جنته.", "السَّلَامُ الْمُؤْمِنُ الْمُهَيْمِنُ"),
    GodNameItem(7, "المُؤْمِنُ", "الذي صدق رسله بآياته وأمّن خلقه من الفزع والظلم", "الذي يؤمّن عباده من الخوف ومن عذابه إن أطاعوه.", "الْمُؤْمِنُ الْمُهَيْمِنُ الْعَزِيزُ"),
    GodNameItem(8, "المُهَيْمِنُ", "المسيطر الشاهد الرقيب الحافظ لكل شيء", "القائم على خلقه بأعمالهم وأرزاقهم وآجالهم، الحافظ لهم.", "الْمُهَيْمِنُ الْعَزِيزُ الْجَبَّارُ"),
    GodNameItem(9, "العَزِيزُ", "القوي الغالب الذي لا يُقهر ولا يُغلب", "الذي له العزة كلها: عزة القوة، وعزة الامتناع، وعزة القهر.", "وَهُوَ الْعَزِيزُ الْحَكِيمُ"),
    GodNameItem(10, "الجَبَّارُ", "الذي يقهر الجبابرة ويجبر قلوب المنكسرين", "الذي ينفذ مشيئته في خلقه رغماً عنهم، ويجبر الكسير ويغني الفقير.", "الْجَبَّارُ الْمُتَكَبِّرُ"),
    GodNameItem(11, "المُتَكَبِّرُ", "المتعالي عن صفات الخلق والشركاء", "الذي له الكبرياء والعظمة والمجد، المنزه عن الظلم والنقص.", "سُبْحَانَ اللَّهِ عَمَّا يُشْرِكُونَ"),
    GodNameItem(12, "الخَالِقُ", "المبدع والمقدّر للأشياء والموجد لها من العدم", "الذي خلق كل المخلوقات بتقدير وإتقان دون مثال سابق.", "اللَّهُ خَالِقُ كُلِّ شَيْءٍ"),
    GodNameItem(13, "البَارِئُ", "الذي أبرأ خلقه وفصلهم وميز بعضهم عن بعض", "الموجد للأشياء بريئة من التفاوت والعيب، مطابقاً لما أراده.", "هُوَ اللَّهُ الْخَالِقُ الْبَارِئُ"),
    GodNameItem(14, "المُصَوِّرُ", "المعطي لكل مخلوق صورته وهيئته الخاصة", "الذي شكّل خلقه في الأرحام وأعطى كل كائن هيئته الفريدة.", "هُوَ الَّذِي يُصَوِّرُكُمْ فِي الْأَرْحَامِ"),
    GodNameItem(15, "الغَفَّارُ", "الكثير المغفرة والستر على ذنوب عباده", "الذي يستر الذنوب في الدنيا ويتجاوز عن عقابها في الآخرة.", "وَإِنِّي لَغَفَّارٌ لِّمَن تَابَ"),
    GodNameItem(16, "القَهَّارُ", "الذي قهر جميع الكائنات وخضعت له الرقاب", "الغالب الذي خضع لعزته وجبروته كل شيء في الكون.", "وَهُوَ الْوَاحِدُ الْقَهَّارُ"),
    GodNameItem(17, "الوَهَّابُ", "الكثير العطايا والهبات دون عوض ولا طلب", "الذي يجود بالنعم والفضل الواسع على عباده بغير سؤال.", "إِنَّكَ أَنتَ الْوَهَّابُ"),
    GodNameItem(18, "الرَّزَّاقُ", "المتكفل بأقوات العباد وخالق أسباب الرزق", "الذي يوصل الأرزاق إلى أبدانهم وأرواحهم بفضله ولطفه.", "إِنَّ اللَّهَ هُوَ الرَّزَّاقُ ذُو الْقُوَّةِ الْمَتِينُ"),
    GodNameItem(19, "الفَتَّاحُ", "الذي يفتح أبواب الرحمة والرزق ويحكم بين عباده", "يفتح على عباده بالعلوم والخيرات، ويقضي بينهم بالحق والعدل.", "وَهُوَ الْفَتَّاحُ الْعَلِيمُ"),
    GodNameItem(20, "العَلِيمُ", "المحيط بكل شيء ظاهره وباطنه وماضيه ومستقبله", "الذي لا يعزب عن علمه مثقال ذرة في الأرض ولا في السماء.", "إِنَّ اللَّهَ بِكُلِّ شَيْءٍ عَلِيمٌ"),
    GodNameItem(21, "القَابِضُ", "الذي يضيق الرزق ويبسطه بحكمته ويقبض الأرواح", "يقبض الأرزاق بحكمته، ويقبض النفوس عند الموت.", "وَاللَّهُ يَقْبِضُ وَيَبْسُطُ"),
    GodNameItem(22, "البَاسِطُ", "الذي يوسع الرزق والرحمة على من يشاء", "ينشر الرزق والنعمة، ويبسط يديه بالتوبة للمذنبين.", "وَإِلَيْهِ تُرْجَعُونَ"),
    GodNameItem(23, "الخَافِضُ", "الذي يخفض الكافرين والطغاة ويذلهم", "يحط من قدر الظالمين والمتكبرين ويضعهم في أسفل درك.", "خَافِضَةٌ رَّافِعَةٌ"),
    GodNameItem(24, "الرَّافِعُ", "الذي يرفع أولياءه وأهل طاعته بالعلم والإيمان", "يرفع أقواماً بالهداية والكرامة في الدنيا والآخرة.", "يَرْفَعِ اللَّهُ الَّذِينَ آمَنُوا مِنكُمْ"),
    GodNameItem(25, "المُعِزُّ", "الذي يهب العزة والتمكين لمن يشاء من عباده", "يعز من أطاعه بالهدى والنصر والنور في القلب.", "تُعِزُّ مَن تَشَاءُ"),
    GodNameItem(26, "المُذِلُّ", "الذي يذل من عصاه واستكبر عن أمره", "يسلب الكرامة عمن تمرد على شرعه ويسلط عليه الهوان.", "وَتُذِلُّ مَن تَشَاءُ"),
    GodNameItem(27, "السَّمِيعُ", "الذي يسمع كل الأصوات والنجوى ولا يشغله سمع عن سمع", "يسمع دبيب النملة السوداء على الصخرة الملساء في الليلة الظلماء.", "إِنَّهُ هُوَ السَّمِيعُ الْبَصِيرُ"),
    GodNameItem(28, "البَصِيرُ", "الذي يرى جميع المبصرات والسرائر الدقيقة", "يرى خفايا النفوس وبواطن الأمور دون حائل.", "وَاللَّهُ بَصِيرٌ بِمَا تَعْمَلُونَ"),
    GodNameItem(29, "الحَكَمُ", "الحاكم العادل الذي لا راد لقضائه وأمره", "الذي يفصل بين الخلائق بعدله وحكمته ولا يظلم أحداً.", "أَفَغَيْرَ اللَّهِ أَبْتَغِي حَكَمًا"),
    GodNameItem(30, "العَدْلُ", "المنزه عن الظلم والجور في أفعاله وأحكامه", "الذي يضع كل شيء في موضعه الصحيح بحكمة وإنصاف.", "وَتَمَّتْ كَلِمَتُ رَبِّكَ صِدْقًا وَعَدْلًا"),
    GodNameItem(31, "اللَّطِيفُ", "البر بعباده الذي يوصل إليهم الإحسان برفق وخفاء", "يعلم دقائق المصالح ويوصلها إلى أصحابها بطرق لا يشعرون بها.", "إِنَّ رَبِّي لَطِيفٌ لِّمَا يَشَاءُ"),
    GodNameItem(32, "الخَبِيرُ", "العالم بحقائق الأشياء وبواطنها الدقيقة", "لا تغيب عنه خافية، خبير بالقلوب وما تكنّه الصدور.", "وَهُوَ الْحَكِيمُ الْخَبِيرُ"),
    GodNameItem(33, "الحَلِيمُ", "الذي لا يعجل بالعقوبة على العاصين لعلهم يتوبون", "يصفح مع القدرة، ويؤخر العقوبة إمهالاً وفضلاً.", "وَاعْلَمُوا أَنَّ اللَّهَ غَفُورٌ حَلِيمٌ"),
    GodNameItem(34, "العَظِيمُ", "الذي لا شيء أعظم منه في ذاته وصفاته وأفعاله", "الذي جاوز حدود العقول في جلاله، وخضعت له الكائنات.", "وَهُوَ الْعَلِيُّ الْعَظِيمُ"),
    GodNameItem(35, "الغَفُورُ", "الذي يغفر الذنوب العظام ويتجاوز عن السيئات", "يستر الزلات ويعفو عن السيئات مهما بلغت إذا تاب العبد.", "وَإِنَّ اللَّهَ لَعَفُوٌّ غَفُورٌ"),
    GodNameItem(36, "الشَّكُورُ", "الذي يجزي بالقليل من العمل الكثير من الثواب", "يقبل اليسير من الطاعات ويعطي عليه الجزيل من الدرجات.", "إِنَّ رَبَّنَا لَغَفُورٌ شَكُورٌ"),
    GodNameItem(37, "العَلِيُّ", "المتعالي في ذاته وقدره وقهره فوق كل شيء", "له العلو المطلق: علو الذات، وعلو القدر، وعلو القهر.", "وَهُوَ الْعَلِيُّ الْكَبِيرُ"),
    GodNameItem(38, "الكَبِيرُ", "صاحب الكبرياء والعظمة التي لا يحيط بها عقل", "كل شيء دونه صغير حقير، وهو الكبير المتعال.", "عَالِمُ الْغَيْبِ وَالشَّهَادَةِ الْكَبِيرُ الْمُتَعَالِ"),
    GodNameItem(39, "الحَفِيظُ", "الحافظ للسماوات والأرض ولأعمال عباده وجوارحهم", "يحفظ الكون من الزوال، ويحفظ عباده الصالحين من الفتن.", "إِنَّ رَبِّي عَلَىٰ كُلِّ شَيْءٍ حَفِيظٌ"),
    GodNameItem(40, "المُقِيتُ", "الخالق للأقوات والمتكفل بإيصالها وحفظها للأبدان", "المقتدر والشهيد والحافظ الذي يقوت كل حي بقدر حاجته.", "وَكَانَ اللَّهُ عَلَىٰ كُلِّ شَيْءٍ مُّقِيتًا"),
    GodNameItem(41, "الحَسِيبُ", "الكافي لعباده والمحاسب لهم على أعمالهم بدقة", "الذي يكفي من توكل عليه، ويحصي أعمال العباد ليوم الحساب.", "وَكَفَىٰ بِاللَّهِ حَسِيبًا"),
    GodNameItem(42, "الجَلِيلُ", "صاحب الجلال والعظمة والصفات الكاملة", "الموصوف بنعوت الجلال والكمال والمجد المطلق.", "تَبَارَكَ اسْمُ رَبِّكَ ذِي الْجَلَالِ وَالْإِكْرَامِ"),
    GodNameItem(43, "الكَرِيمُ", "الكثير الخير والصفح، الذي يعطي بلا مقابل", "الذي يعطي من غير سؤال، ويتجاوز عن الذنوب ويكرم أولياءه.", "يَا أَيُّهَا الْإِنسَانُ مَا غَرَّكَ بِرَبِّكَ الْكَرِيمِ"),
    GodNameItem(44, "الرَّقِيبُ", "المطّلع على ما أكنّته الصدور والضمائر وحركات الجوارح", "لا يغفل عن مراقبة خلقه لحظة واحدة.", "إِنَّ اللَّهَ كَانَ عَلَيْكُمْ رَقِيبًا"),
    GodNameItem(45, "المُجِيبُ", "الذي يجيب دعاء الداعين وسؤال السائلين", "يقابل الدعاء بالإجابة والاضطرار بالإغاثة والنجدة.", "إِنَّ رَبِّي قَرِيبٌ مُّجِيبٌ"),
    GodNameItem(46, "الوَاسِعُ", "الذي وسع علمه ورحمته وغناه كل موجود", "لا نهاية لفضله وجوده، وسعت رحمته كل شيء.", "وَاللَّهُ وَاسِعٌ عَلِيمٌ"),
    GodNameItem(47, "الحَكِيمُ", "الذي يضع الأشياء في مواضعها بإتقان وحكمة تامة", "المبرأ من العبث والخلل في تدبير كونه وشرعه.", "إِنَّكَ أَنتَ الْعَلِيمُ الْحَكِيمُ"),
    GodNameItem(48, "الوَدُودُ", "المحب لأوليائه والمتحبب إلى خلقه بالنعم", "يحب عباده المتقين ويجعل لهم وداً ومحبة في القلوب.", "وَهُوَ الْغَفُورُ الْوَدُودُ"),
    GodNameItem(49, "المَجِيدُ", "الواسع الكرم العظيم القدر البالغ أقصى الشرف", "الموصوف بالمجد والعظمة والإحسان الواسع.", "إِنَّهُ حَمِيدٌ مَّجِيدٌ"),
    GodNameItem(50, "البَاعِثُ", "الذي يبعث الموتى من القبور ويبعث الرسل", "يحيي الخلائق بعد فنائهم ليوم الحساب والجزاء العادل.", "وَأَنَّ اللَّهَ يَبْعَثُ مَن فِي الْقُبُورِ"),
    GodNameItem(51, "الشَّهِيدُ", "الحاضر المطّلع على كل شيء الشاهد على أعمال الخلق", "لا يعزب عنه غائب، شهد لخلقه بالتوحيد والعدل.", "إِنَّ اللَّهَ عَلَىٰ كُلِّ شَيْءٍ شَهِيدٌ"),
    GodNameItem(52, "الحَقُّ", "الثابت الموجود يقيناً الذي لا شك في ربوبيته", "هو الحق المبين، وقوله حق، ووعده حق، ولقاؤه حق.", "فَذَٰلِكُمُ اللَّهُ رَبُّكُمُ الْحَقُّ"),
    GodNameItem(53, "الوَكِيلُ", "المتفرد بتدبير خلقه والكافي لمن توكل عليه", "من فوّض أمره إليه كفاه، وهو الحافظ الحقيقي لكل شيء.", "وَتَوَكَّلْ عَلَى اللَّهِ وَكَفَىٰ بِاللَّهِ وَكِيلًا"),
    GodNameItem(54, "القَوِيُّ", "صاحب القدرة التامة التي لا يعتريها عجز ولا وهن", "الذي لا يعجزه شيء في الأرض ولا في السماء.", "إِنَّ اللَّهَ لَقَوِيٌّ عَزِيزٌ"),
    GodNameItem(55, "المَتِينُ", "الشديد القوة الذي لا تنقطع قدرته ولا يمسه تعب", "المتناهي في الثبات والقوة والعزم والصلابة الإلهية.", "إِنَّ اللَّهَ هُوَ الرَّزَّاقُ ذُو الْقُوَّةِ الْمَتِينُ"),
    GodNameItem(56, "الوَلِيُّ", "الناصر المعين لأوليائه المتولي لشؤون خلقه", "يتولى الصالحين بحفظه ورعايته ومحبته وتوفيقه.", "وَهُوَ الْوَلِيُّ الْحَمِيدُ"),
    GodNameItem(57, "الحَمِيدُ", "المستحق للحمد والثناء في السراء والضراء", "المحمود بذاته وأسمائه وصفاته وأفعاله في كل وقت.", "يَا أَيُّهَا النَّاسُ أَنتُمُ الْفُقَرَاءُ إِلَى اللَّهِ وَاللَّهُ هُوَ الْغَنِيُّ الْحَمِيدُ"),
    GodNameItem(58, "المُحْصِي", "الذي أحاط بكل شيء علماً وأحصى كل شيء عدداً", "لا تفوته دقيقة ولا جليلة، أحصى خطوات العباد وأعمالهم.", "وَأَحْصَىٰ كُلَّ شَيْءٍ عَدَدًا"),
    GodNameItem(59, "المُبْدِئُ", "الذي أنشأ الخلق وابتدأه من غير مثال سابق", "أوجد الخلائق كلها أول مرة من العدم المحض.", "إِنَّهُ هُوَ يُبْدِئُ وَيُعِيدُ"),
    GodNameItem(60, "المُعِيدُ", "الذي يعيد الخلق بعد الموت والفناء كهيئتهم الأولى", "يرجع الخلائق أحياء يوم القيامة كما بدأهم أول مرة.", "كَمَا بَدَأْنَا أَوَّلَ خَلْقٍ نُّعِيدُهُ"),
    GodNameItem(61, "المُحْيِي", "الذي يحيي الأجساد والأرواح بالقدرة والرحمة", "يحيي الأرض بعد موتها بالغيث، ويحيي القلوب بالقرآن والإيمان.", "يُحْيِي وَيُمِيتُ وَإِلَيْهِ تُرْجَعُونَ"),
    GodNameItem(62, "المُمِيتُ", "المقدر للموت والوفاة على كل حي", "يسلب الحياة عمن يشاء حين ينقضي أجله المقدر.", "الَّذِي خَلَقَ الْمَوْتَ وَالْحَيَاةَ"),
    GodNameItem(63, "الحَيُّ", "الباقي دائماً الذي له الحياة الكاملة الأزلية الأبدية", "حياة لا بداية لها ولا نهاية، لا يعتريه موت ولا سِنة ولا نوم.", "اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ"),
    GodNameItem(64, "القَيُّومُ", "القائم بنفسه المقيم لكل ما سواه من المخلوقات", "المستغني عن الخلائق، والمدبر لجميع السماوات والأرض.", "لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ"),
    GodNameItem(65, "الوَاجِدُ", "الذي يجد كل ما يطلبه ولا يعوزه شيء", "الغني الذي لا يحتاج إلى معين ولا يفوته مراد.", "وَوَجَدَكَ ضَالًّا فَهَدَىٰ"),
    GodNameItem(66, "المَاجِدُ", "صاحب الشرف والمجد والكمال والمحامد العالية", "الكريم المفضال واسع العطاء والفضل المترادف.", "ذُو الْعَرْشِ الْمَجِيدُ"),
    GodNameItem(67, "الوَاحِدُ", "الفرد الذي لا شريك له في ذاته ولا في صفاته", "المتفرد بالألوهية والربوبية والتدبير في الكون.", "قُلْ هُوَ اللَّهُ أَحَدٌ"),
    GodNameItem(68, "الأَحَدُ", "المنزه عن التعدد والتركيب والشبيه والمثيل", "ليس كمثله شيء وهو السميع البصير.", "اللَّهُ الصَّمَدُ"),
    GodNameItem(69, "الصَّمَدُ", "السيد المطاع الذي تُقصد إليه الحوائج وتلجأ إليه الخلائق", "الذي لا جوف له، الكامل في سؤدده وعظمته وحاجات خلقه إليه.", "لَمْ يَلِدْ وَلَمْ يُولَدْ"),
    GodNameItem(70, "القَادِرُ", "المتمكن من فعل كل ما تقتضيه مشيئته وحكمته", "إن أراد شيئاً قال له كن فيكون، لا يعجزه شيء.", "قُلْ هُوَ الْقَادِرُ عَلَىٰ أَن يَبْعَثَ عَلَيْكُمْ عَذَابًا"),
    GodNameItem(71, "المُقْتَدِرُ", "التام الاقتدار الذي لا يقف أمامه عائق", "المتصف بالقدرة العظيمة القاهرة على تدبير الأكوان.", "فِي مَقْعَدِ صِدْقٍ عِندَ مَلِيكٍ مُّقْتَدِرٍ"),
    GodNameItem(72, "المُقَدِّمُ", "الذي يقدم الأشياء في مواضعها وفق حكمته البالغة", "يقدم من يشاء بالهداية والرفعة والمنازل العالية.", "أَنتَ الْمُقَدِّمُ وَأَنتَ الْمُؤَخِّرُ"),
    GodNameItem(73, "المُؤَخِّرُ", "الذي يؤخر الأشياء بحكمته إمهالاً وترتيباً", "يؤخر ما يشاء من الأمور لحكمة لا يعلمها إلا هو.", "لَا إِلَهَ إِلَّا أَنتَ"),
    GodNameItem(74, "الأَوَّلُ", "الذي ليس قبله شيء، الأزلي بلا بداية", "السابق على كل الكائنات والموجودات كلها.", "هُوَ الْأَوَّلُ وَالْآخِرُ"),
    GodNameItem(75, "الآخِرُ", "الذي ليس بعده شيء، الباقي بعد فناء خلقه", "الأبدي الذي يبقى وجهه ذو الجلال والإكرام.", "كُلُّ مَنْ عَلَيْهَا فَانٍ"),
    GodNameItem(76, "الظَّاهِرُ", "العالي فوق كل شيء بآياته وقدرته وقهر سلطانه", "الذي ظهرت دلائل عظمته وحكمته في مخلوقاته كلها.", "وَالظَّاهِرُ وَالْبَاطِنُ"),
    GodNameItem(77, "البَاطِنُ", "المحتجب عن أبصار خلقه المحيط بكل خفي", "الذي بطن علمه في دقائق الأمور وخفايا القلوب.", "وَهُوَ بِكُلِّ شَيْءٍ عَلِيمٌ"),
    GodNameItem(78, "الوَالِي", "المتولي لأمور الخلائق بالتدبير والنصرة", "المالك لجميع الأشياء المتصرف فيها بمشيئته.", "وَمَا لَهُم مِّن دُونِهِ مِن وَالٍ"),
    GodNameItem(79, "المُتَعَالِي", "المتعالي عن أوهام الخلائق وصفات النقص", "المنزه عن كل شبه ونقص والمترفع عن الشركاء.", "الْكَبِيرُ الْمُتَعَالِ"),
    GodNameItem(80, "البَرُّ", "الصادق في وعده المحسن إلى عباده ببره ولطفه", "المعطي للخير والإحسان، العطوف الرحيم بخلقه.", "إِنَّا كُنَّا مِن قَبْلُ نَدْعُوهُ إِنَّهُ هُوَ الْبَرُّ الرَّحِيمُ"),
    GodNameItem(81, "التَّوَّابُ", "الذي يقبل توبة التائبين مرة بعد مرة", "يوفق عباده للتوبة ثم يقبلها منهم ويعفو عنهم.", "إِنَّ اللَّهَ كَانَ تَوَّابًا رَّحِيمًا"),
    GodNameItem(82, "المُنْتَقِمُ", "المعاقب للظالمين والمجرمين بعد الإنذار والإمهال", "يقصم الجبابرة وينصر المظلومين بعدله وحكمته.", "إِنَّا مِنَ الْمُجْرِمِينَ مُنتَقِمُونَ"),
    GodNameItem(83, "العَفُوُّ", "الذي يمحو السيئات والذنوب ويترك العقاب", "أبلغ من الغفور؛ إذ يمحو أثر الذنب بالكلية من صحائف العباد.", "إِنَّ اللَّهَ كَانَ عَفُوًّا غَفُورًا"),
    GodNameItem(84, "الرَّءُوفُ", "الشديد الرأفة والرحمة الشاملة بالعباد", "الرأفة أعلى مراتب الرحمة وأرقها على العباد.", "وَإِنَّ اللَّهَ بِكُمْ لَرَءُوفٌ رَّحِيمٌ"),
    GodNameItem(85, "مَالِكُ المُلْكِ", "المتصرف في ملكه العام كيف يشاء يؤتي وينزع", "له الملك التام في الدنيا والآخرة لا معقب لحكمه.", "قُلِ اللَّهُمَّ مَالِكَ الْمُلْكِ تُؤْتِي الْمُلْكَ مَن تَشَاءُ"),
    GodNameItem(86, "ذُو الجَلَالِ وَالإِكْرَامِ", "المستحق للتعظيم والإجلال والكرم المطلق", "أهل لأن يُجل ويُعظم ولا يُعصى، ويكرم أولياءه بجنته.", "وَيَبْقَىٰ وَجْهُ رَبِّكَ ذُو الْجَلَالِ وَالْإِكْرَامِ"),
    GodNameItem(87, "المُقْسِطُ", "العادل المنصف الذي ينتصف للمظلوم من الظالم", "الذي يقيم القسط والعدل التام بين الخلائق.", "إِنَّ اللَّهَ يُحِبُّ الْمُقْسِطِينَ"),
    GodNameItem(88, "الجَامِعُ", "الذي يجمع الخلائق ليوم لا ريب فيه ويجمع الفضائل", "يجمع بين المتماثلات والمتفرقات، ويجمع الناس في المحشر.", "رَبَّنَا إِنَّكَ جَامِعُ النَّاسِ لِيَوْمٍ لَّا رَيْبَ فِيهِ"),
    GodNameItem(89, "الغَنِيُّ", "المستغني عن كل شيء بذاته وخلقه فقراء إليه", "لا تنفعه طاعة الطائعين ولا تضره معصية العاصين.", "وَاللَّهُ الْغَنِيُّ وَأَنتُمُ الْفُقَرَاءُ"),
    GodNameItem(90, "المُغْنِي", "الذي يسد حاجات عباده ويغنيهم بفضله وكرمه", "يغني من يشاء بالقناعة في القلب أو بالمال والرزق.", "وَأَنَّهُ هُوَ أَغْنَىٰ وَأَقْنَىٰ"),
    GodNameItem(91, "المَانِعُ", "الذي يمنع من يشاء بحكمته لحفظه أو لابتلائه", "ما يفتح الله للناس من رحمة فلا ممسك لها وما يمسك فلا مرسل له.", "مَّا يَفْتَحِ اللَّهُ لِلنَّاسِ مِن رَّحْمَةٍ فَلَا مُمْسِكَ لَهَا"),
    GodNameItem(92, "الضَّارُّ", "المقدر للضر والابتلاء لحكمة بالغة وتمحيص", "لا يصيب العبد ضر إلا بإذنه ومشايئته لحكمة.", "وَإِن يَمْسَسْكَ اللَّهُ بِضُرٍّ فَلَا كَاشِفَ لَهُ إِلَّا هُوَ"),
    GodNameItem(93, "النَّافِعُ", "المقدر للنفع والخير والبركات في الأكوان", "كل خير ونعمة وصحة ونفع مصدرها الله تعالى وحده.", "وَإِن يَمْسَسْكَ بِخَيْرٍ فَهُوَ عَلَىٰ كُلِّ شَيْءٍ قَدِيرٌ"),
    GodNameItem(94, "النُّورُ", "الذي نوّر السماوات والأرض بنوره وبهديه", "منور قلوب المؤمنين بالإيمان وهداية الكتب والرسل.", "اللَّهُ نُورُ السَّمَاوَاتِ وَالْأَرْضِ"),
    GodNameItem(95, "الهَادِي", "المرشد للخلائق إلى مصالحها وهادي القلوب للحق", "يهدي من يشاء إلى صراط مستقيم ويبصره بالنجاة.", "وَإِنَّ اللَّهَ لَهَادِ الَّذِينَ آمَنُوا إِلَىٰ صِرَاطٍ مُّسْتَقِيمٍ"),
    GodNameItem(96, "البَدِيعُ", "الذي أبدع الأكوان وفطرها على غير مثال سابق", "المتصف بالجمال والابتكار المطلق في خلقه وبديع صنعه.", "بَدِيعُ السَّمَاوَاتِ وَالْأَرْضِ"),
    GodNameItem(97, "البَاقِي", "الدائم الوجود الذي لا يلحقه فناء ولا انقطاع", "الأبدي سرمداً، تفنى المخلوقات كلها وهو الحي الباقي.", "وَيَبْقَىٰ وَجْهُ رَبِّكَ ذُو الْجَلَالِ وَالْإِكْرَامِ"),
    GodNameItem(98, "الوَارِثُ", "الباقي بعد فناء جميع المخلوقات وإليه يرجع كل شيء", "يرث الأرض ومن عليها بعد انقضاء الدنيا.", "وَإِنَّا لَنَحْنُ نُحْيِي وَنُمِيتُ وَنَحْنُ الْوَارِثُونَ"),
    GodNameItem(99, "الصَّبُورُ", "الذي لا يعاجل العصاة بالعقوبة ويمهلم برحمته", "الحليم الصبور على أذى المشركين وإعراض الجاحدين.", "إِنَّ اللَّهَ مَعَ الصَّابِرِينَ")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GodNamesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedName by remember { mutableStateOf<GodNameItem?>(null) }

    val filteredNames = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            ALL_99_GOD_NAMES
        } else {
            ALL_99_GOD_NAMES.filter {
                it.name.contains(searchQuery.trim()) ||
                        it.meaning.contains(searchQuery.trim()) ||
                        it.explanation.contains(searchQuery.trim())
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFFF9F7F2) // Warm soft beige matching Screenshot 2
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Header matching Screenshot 2 exactly (Warm beige curve, back button, 3D Islamic green star emblem with "الله", title)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEDE6D9), // Beige warm header matching image 2
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back Button in circular container matching screenshot
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDED6C7))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color(0xFF4A4A4A)
                            )
                        }

                        // Center Emblem & Title matching image 2
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // 3D-styled green Islamic octagram badge with "الله" in calligraphy
                            Surface(
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(6.dp, RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF689F38), // Soft green Islamic badge matching image 2
                                border = BorderStroke(2.dp, Color(0xFFC5E1A5))
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFF8BC34A), Color(0xFF558B2F))
                                            )
                                        )
                                ) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFF9F7F2),
                                        border = BorderStroke(1.dp, Color(0xFFDCEDC8))
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "الله",
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF33691E)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "أسماء الله الحسنى",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2A504B)
                            )
                        }

                        // Placeholder spacer to balance the back button
                        Spacer(modifier = Modifier.size(42.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        placeholder = { Text("ابحث في أسماء الله الحسنى ومعانيها...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Color(0xFF2A504B)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                            focusedBorderColor = Color(0xFF689F38),
                            unfocusedBorderColor = Color(0xFFD7CCC8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. The 3-Column Grid matching Screenshot 2 exactly!
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredNames, key = { it.number }) { item ->
                    GodNameCard(
                        item = item,
                        onClick = { selectedName = item }
                    )
                }
            }
        }
    }

    // Modal Details Dialog on click
    selectedName?.let { nameItem ->
        GodNameDetailDialog(
            nameItem = nameItem,
            onDismiss = { selectedName = null }
        )
    }
}

@Composable
fun GodNameCard(
    item: GodNameItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color.White, // White cards matching screenshot 2
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFE8E5DD))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.name,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp
                )
            }
        }
    }
}

@Composable
fun GodNameDetailDialog(
    nameItem: GodNameItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tasbihCount by remember { mutableIntStateOf(0) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar")
            }
        }
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge with name
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF1F8E9),
                    border = BorderStroke(1.5.dp, Color(0xFF8BC34A)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = nameItem.name,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = "الاسم رقم ${nameItem.number} من أسماء الله الحسنى",
                            fontSize = 12.sp,
                            color = Color(0xFF558B2F)
                        )
                    }
                }

                // Meaning Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "المعنى والدلالة الإيمانية:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = nameItem.meaning,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF334155)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = nameItem.explanation,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }

                if (nameItem.quranRef.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEFCE8),
                        border = BorderStroke(1.dp, Color(0xFFFEF08A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📖", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "قال تعالى: «${nameItem.quranRef}»",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF854D0E)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Tasbih Counter
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            tasbihCount++
                            triggerVibration()
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.5.dp, Color(0xFFA5D6A7))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "تسبيح وتعبد بالاسم 📿",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                text = "اضغط للتسبيح والاستغفار",
                                fontSize = 11.sp,
                                color = Color(0xFF388E3C)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$tasbihCount",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1B5E20)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { tasbihCount = 0 },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "تصفير", tint = Color(0xFF2E7D32))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: Speech Audio & Share & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Speech Button
                    OutlinedButton(
                        onClick = {
                            val textToSpeak = "${nameItem.name}. ${nameItem.meaning}. ${nameItem.explanation}"
                            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "god_name_tts")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("استماع 🔊", fontSize = 12.sp)
                    }

                    // Share Button
                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "أسماء الله الحسنى: ${nameItem.name}")
                                putExtra(Intent.EXTRA_TEXT, "✨ من أسماء الله الحسنى:\n【 ${nameItem.name} 】\n\n📌 المعنى: ${nameItem.meaning}\n\n📖 قال تعالى: «${nameItem.quranRef}»\n\nتطبيق رفيق الطالب")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الاسم المبارك"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة 📤", fontSize = 12.sp)
                    }

                    // Close Button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("إغلاق", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
