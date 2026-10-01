require 'minitest/autorun'
require 'tmpdir'
require 'fileutils'
require 'open3'

class CheckScriptMigrationTest < Minitest::Test
  ROOT = File.expand_path('../..', __dir__)
  SCRIPTS = %w[scripts/dev/check/scan-javadoc-issues.sh scripts/dev/check/scan-lombok-issues.sh scripts/check-commercialization-readiness.sh].freeze

  def setup
    @tmp = Dir.mktmpdir('apex script tests ')
    @root = File.join(@tmp, 'project space')
    (SCRIPTS + ['scripts/lib/common.sh']).each do |path|
      target = File.join(@root, path)
      FileUtils.mkdir_p(File.dirname(target))
      FileUtils.cp(File.join(ROOT, path), target)
    end
    write('pom.xml', '<id>basic</id><id>standard</id><id>enterprise</id>')
    %w[application domain infrastructure common interfaces].each do |name|
      FileUtils.mkdir_p(File.join(@root, "apex-#{name}/src/main/java"))
    end
    FileUtils.mkdir_p(File.join(@root, 'apex-interfaces/src/main/resources/db/migration/mysql'))
  end

  def teardown
    FileUtils.remove_entry(@tmp)
  end

  def write(path, content)
    path = File.join(@root, path)
    FileUtils.mkdir_p(File.dirname(path))
    File.write(path, content)
  end

  def run_script(index, env = {})
    path = File.join(@root, SCRIPTS[index])
    content = File.read(path)
    refute_includes content, 'PROJECT_ROOT="/Users/work/apex-scaffold"', 'must locate copied project without touching real reports'
    Open3.capture3(env, '/bin/bash', path, chdir: @tmp)
  end

  def report(name)
    File.readlines(File.join(@root, name), chomp: true)
  end

  def test_empty_scans_and_unrelated_reports
    write('javadoc-issues-custom.txt', 'keep')
    write('lombok-issues-custom.txt', 'keep')
    2.times do |i|
      out, err, status = run_script(i)
      assert status.success?, out + err
      assert_includes out, '总计: 0'
    end
    assert_equal 'keep', File.read(File.join(@root, 'javadoc-issues-custom.txt'))
    assert_equal 'keep', File.read(File.join(@root, 'lombok-issues-custom.txt'))
  end

  def test_javadoc_semantics_and_special_paths
    path = 'apex-domain/src/main/java/space dir/Back\\Slash.java'
    write(path, "public class Plain {\n    public String one() {}\n    public String two() {}\n    public String three() {}\n    public String four() {}\n}\n")
    documented = 'apex-domain/src/main/java/Documented.java'
    write(documented, "/** docs */\npublic class Documented {}\n")
    %w[target .trae .git .idea .vscode node_modules .settings build test].each do |dir|
      write("apex-domain/src/main/java/#{dir}/Hidden.java", "public class Hidden {}\n")
    end
    out, err, status = run_script(0)
    assert status.success?, out + err
    refute_includes err, '[ERROR]'
    assert_equal [path], report('javadoc-issues-class.txt')
    assert_equal [path], report('javadoc-issues-method.txt')
    assert_equal [documented], report('javadoc-issues-author.txt')
    assert_equal [documented], report('javadoc-issues-since.txt')
  end

  def test_lombok_semantics
    dto = 'apex-application/src/main/java/dto/Space Request.java'
    service = 'apex-domain/src/main/java/ManualService.java'
    po = 'apex-infrastructure/src/main/java/po/ItemPO.java'
    write(dto, "public class Request {\npublic String getValue() {}\n}\n")
    write(service, "public class ManualService {\npublic ManualService() {}\nprivate Logger log;\n}\n")
    write(po, "@NoArgsConstructor\npublic class ItemPO {}\n")
    write('apex-application/src/main/java/dto/GoodDTO.java', '@Builder')
    write('apex-domain/src/main/java/test/HiddenService.java', 'public HiddenService() {}')
    write('apex-domain/src/main/java/target/HiddenDTO.java', 'public String getHidden() {}')
    out, err, status = run_script(1)
    assert status.success?, out + err
    refute_includes err, '[ERROR]'
    assert_equal [dto], report('lombok-issues-getter.txt')
    assert_equal [dto], report('lombok-issues-builder.txt')
    assert_equal [service], report('lombok-issues-constructor.txt')
    assert_equal [service], report('lombok-issues-logger.txt')
    assert_equal [po], report('lombok-issues-po-constructor.txt')
  end

  def test_commercial_runs_all_checks
    out, err, status = run_script(2)
    assert_equal 1, status.exitstatus, out + err
    assert_includes out, '检查8: 测试覆盖'
    assert_includes out, '检查汇总'
    assert_includes out + err, '测试文件总数: 0'
    assert_includes out, '总体评分:'
  end

  def test_find_errors_propagate
    write('bin/find', "#!/bin/bash\nexit 37\n")
    FileUtils.chmod(0o755, File.join(@root, 'bin/find'))
    3.times do |i|
      out, err, status = run_script(i, 'PATH' => "#{@root}/bin:#{ENV.fetch('PATH')}")
      assert_equal 37, status.exitstatus, out + err
      refute_includes out, '扫描完成'
      refute_includes out, '总体评分:'
    end
  end

  def test_read_errors_propagate
    write('apex-domain/src/main/java/Plain.java', 'public class Plain {}')
    write('apex-domain/src/main/java/com/apex/scaffold/domain/model/entity/SampleEntity.java', 'plain')
    write('bin/grep', "#!/bin/bash\nexit 42\n")
    FileUtils.chmod(0o755, File.join(@root, 'bin/grep'))
    3.times do |i|
      out, err, status = run_script(i, 'PATH' => "#{@root}/bin:#{ENV.fetch('PATH')}")
      assert_equal 42, status.exitstatus, out + err
      refute_includes out, '扫描完成'
      refute_includes out, '总体评分:'
    end
  end
end
